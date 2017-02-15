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
	private static final Integer LEVEL = null; // 1..9
	private File dest;

	public static class Exclude {
		public String name;

		public void setName(final String name) {
			this.name = name;
		}
	}

	private final List<FileSet> filesets = new ArrayList<FileSet>();
	private final List<Exclude> excludes = new ArrayList<Exclude>();

	/** Use for <exclude */
	public synchronized Exclude createExclude() {
		final Exclude e = new Exclude();
		this.excludes.add(e);
		return e;
	}

	public void setDest(final File dst) {
		this.dest = dst;
	}

	public void addFileset(final FileSet set) {
		this.filesets.add(set);
	}

	private String readFile(final ZipEntry entry, final InputStream s) throws IOException {
		final int len = (int) entry.getSize();
		final byte[] dat = new byte[len];
		int pos = 0;
		int cnt;
		while (0 < (cnt = s.read(dat, pos, len - pos)))
			pos += cnt;
		if (pos != len)
			throw new IOException("readFile got:" + pos + " len:" + len);
		final String str = new String(dat);
		return str.endsWith("\n") ? str : str + "\n";
	}

	private static void addToZipFile(final ZipEntry entry, final ZipOutputStream zos, final InputStream fis)
			throws FileNotFoundException, IOException {
		zos.putNextEntry(entry);
		final byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) > 0) {
			zos.write(bytes, 0, length);
		}
		zos.flush();
		zos.closeEntry();
	}

	private void addEntryContent(final ZipOutputStream zos, final ZipEntry entry, final InputStream in,
			final Map<String, String> services, final Set<String> once) throws IOException {
		for (final Exclude e : this.excludes) {
			if (e.name != null) {
				if (SelectorUtils.match(e.name, entry.getName()))
					return;
			}
		}
		if (entry.getName().startsWith("META-INF/services/")) {
			final String neu = readFile(entry, in);
			final String old = services.get(entry.getName());
			if (old == null)
				services.put(entry.getName(), neu);
			else
				services.put(entry.getName(), old + neu);
			return;
		}
		if (!once.add(entry.getName())) {
			System.out.println("!!! SKIP Duplicate: " + entry.getName());
			return;
		}
		addToZipFile(entry, zos, in);
	}

	private void addZipFileContent(final ZipOutputStream zos, final File file, final Map<String, String> services,
			final Set<String> once) throws IOException {
		try (final ZipFile zip = new ZipFile(file)) {
			for (final Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
				final ZipEntry entry = e.nextElement();
				if (entry.isDirectory())
					continue; // do not include directories
				try (final InputStream s = zip.getInputStream(entry)) {
					addEntryContent(zos, entry, s, services, once);
				}
			}
		}
	}

	private void addFileResourceContent(final ZipOutputStream zos, final FileResource resource,
			final Map<String, String> services, final Set<String> once) throws IOException {
		final ZipEntry entry = new ZipEntry(resource.getName().replace('\\', '/'));
		entry.setTime(resource.getLastModified());
		try (final InputStream in = resource.getInputStream()) {
			addEntryContent(zos, entry, in, services, once);
		}
	}

	@Override
	public void execute() throws BuildException {
		final Set<String> once = new HashSet<>();
		final Map<String, String> services = new HashMap<>();
		try (final FileOutputStream fos = new FileOutputStream(this.dest, false);
				final ZipOutputStream zos = new ZipOutputStream(fos)) {
			if (LEVEL != null)
				zos.setLevel(LEVEL);
			for (final FileSet fileSet : this.filesets) {
				final DirectoryScanner ds = fileSet.getDirectoryScanner();
				final Iterator<?> iter = new FileResourceIterator(fileSet.getDir(), ds.getIncludedFiles());
				while (iter.hasNext()) {
					final FileResource resource = (FileResource) iter.next();
					final File file = resource.getFile();
					final String name = file.getName();
					if (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".war")
							|| name.endsWith(".ear")) {
						addZipFileContent(zos, file, services, once);
					} else {
						addFileResourceContent(zos, resource, services, once);
					}
				} // each resource
			} // each fileSet
			for (final Map.Entry<String, String> service : services.entrySet()) {
				zos.putNextEntry(new ZipEntry(service.getKey()));
				zos.write(service.getValue().getBytes());
			}
		} catch (final IOException e) {
			if (!this.dest.delete())
				System.out.println("DELETE " + this.dest.getAbsolutePath());
			e.printStackTrace();
			throw new BuildException(e);
		}
	}
}