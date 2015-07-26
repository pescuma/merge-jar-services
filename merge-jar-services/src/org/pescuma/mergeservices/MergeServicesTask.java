package org.pescuma.mergeservices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.FileResource;
import org.apache.tools.ant.types.resources.FileResourceIterator;
import org.apache.tools.ant.types.selectors.SelectorUtils;

public final class MergeServicesTask extends Task {

	private File dest;
	private final List<FileSet> filesets = new ArrayList<FileSet>();
	private final List<Exclude> excludes = new ArrayList<>();

	public static class Exclude {
		public String name;
		public void setName(final String name) { this.name = name; }
	}

	/** Use for <exclude name="filename" /> */
	public synchronized Exclude createExclude() {
		final Exclude e = new Exclude();
		this.excludes.add(e);
		return e;
	}

	/**
	 * Set the destination for the jar/zip file
	 * @param dst Filename
	 */
	public void setDest   (final File    dst) {
		this.dest = dst;
	}

	/**
	 * Used by <fileset dir="dirName"></fileset>
	 * @param set
	 */
	public void addFileset(final FileSet set) {
		this.filesets.add(set);
	}

	/**
	 * Read an File from Service dir an make sure it ends with newline
	 * @param entry         information about the file size
	 * @param s				Source of the file content
	 * @return				Text of the Service file
	 * @throws IOException
	 */
	private String readServiceFile(final ZipEntry entry, final InputStream s) throws IOException {
		final int    len = (int)entry.getSize();
		final byte[] dat = new byte[len];
		int pos = 0;
		int cnt;
		while(0 < (cnt = s.read(dat, pos, len- pos))) pos += cnt;
		if(pos != len) throw new IOException("readFile got:"+pos+" len:"+len);
		final String str = new String(dat);
		return str.endsWith("\n") ? str : str + "\n";
	}

	/**
	 * Copy an file from one zip file to another zip file
	 * @param entry
	 * @param zos
	 * @param fis
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void addToZipFile(final ZipEntry entry, final ZipOutputStream zos, final InputStream fis) throws FileNotFoundException, IOException {
		int size = 0;
		try {
			zos.putNextEntry(entry);
			final byte[] bytes = new byte[1024];
			int length;
			while ((length = fis.read(bytes)) > 0) { zos.write(bytes, 0, length); size+=length; }
			zos.flush();
			zos.closeEntry();
		} catch(final Throwable t) {
			System.out.println("addToZipFile("+entry.getName()+" "+size+"/"+entry.getSize()+")");
			throw t;
		}
	}

	/**
	 * Add an entry, copy if not an META-INF/service else merrge them
	 * @param zos
	 * @param entry
	 * @param in
	 * @param services
	 * @param once
	 * @throws IOException
	 */
	private void addEntryContent(final ZipOutputStream zos, final ZipEntry entry, final InputStream  in, final Map<String, String> services, final Set<String> once) throws IOException {
		for(final Exclude e : this.excludes) { if(e.name != null) { if(SelectorUtils.match(e.name, entry.getName())) return; } }
		if (entry.getName().startsWith("META-INF/services/")) {
			final String neu = readServiceFile(entry, in);
			final String old = services.get(entry.getName());
			if(old == null) services.put(entry.getName(), neu);
			else            services.put(entry.getName(), old+neu);
			return;
		}
		if(!once.add(entry.getName())) {
			System.out.println("!!! SKIP Duplicate: "+entry.getName());
			return;
		}
		addToZipFile(entry, zos, in);
	}

	private void addZipFileContent(final ZipOutputStream zos, final File file, final Map<String, String> services, final Set<String> once) throws IOException {
		try(final ZipFile zip = new ZipFile(file)) {
			for (final Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
				final ZipEntry entry = e.nextElement();
				if(entry.isDirectory()) continue; // do not include directories
				try(final InputStream s = zip.getInputStream(entry)) { addEntryContent(zos, entry, s, services, once); }
			}
		}
	}

	/**
	 * Add normal file to the zip file
	 * @param zos
	 * @param resource
	 * @param services
	 * @param once
	 * @throws IOException
	 */
	private void addFileResourceContent(final ZipOutputStream zos, final FileResource resource, final Map<String, String> services, final Set<String> once) throws IOException {
		final ZipEntry entry = new ZipEntry(resource.getName().replace('\\', '/'));
		entry.setTime(resource.getLastModified());
		try(final InputStream  in  = resource.getInputStream()) { addEntryContent(zos, entry, in, services, once); }
	}

	/**
	 * Main function called by the ant task
	 */
	@Override
	public void execute() throws BuildException {
		final Set<String> once = new HashSet<>();
		// Map of all used services
		final Map<String, String> services = new HashMap<>();
		// Open output file (new JDK-8 style)
		try(	final FileOutputStream fos = new FileOutputStream(this.dest, false);
				final ZipOutputStream  zos = new ZipOutputStream (fos)) {
			for (final FileSet fileSet : this.filesets) {
				final DirectoryScanner ds = fileSet.getDirectoryScanner();
				final Iterator<?> iter = new FileResourceIterator(getProject(), fileSet.getDir(), ds.getIncludedFiles());
				while (iter.hasNext()) {
					final FileResource resource = (FileResource) iter.next();
					final File file = resource.getFile();
					final String name = file.getName();
					if(name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".war") || name.endsWith(".ear")) {
						addZipFileContent     (zos, file, services, once);
					} else {
						addFileResourceContent(zos, resource, services, once);
					}
				}
			}
			// After the "normal" files write the merged META-INF/Services
			for(final Map.Entry<String,String> service : services.entrySet()) {
				zos.putNextEntry(new ZipEntry(service.getKey()));
				zos.write(service.getValue().getBytes());
			}
		}  catch (final IOException e) {
			if(!this.dest.delete()) System.out.println("DELETE "+this.dest.getAbsolutePath());
			e.printStackTrace();
			throw new BuildException(e);
		}
	}
}
