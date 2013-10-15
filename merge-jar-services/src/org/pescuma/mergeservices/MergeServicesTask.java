package org.pescuma.mergeservices;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.FileResource;
import org.apache.tools.ant.types.resources.FileResourceIterator;

public class MergeServicesTask extends Task {
	
	private File dest;
	private final List<FileSet> filesets = new ArrayList<FileSet>();
	
	public void setDest(File dest) {
		this.dest = dest;
	}
	
	public void addFileset(FileSet set) {
		filesets.add(set);
	}
	
	@Override
	public void execute() throws BuildException {
		for (FileSet fileSet : filesets) {
			process(fileSet);
		}
	}
	
	private void process(FileSet fileSet) {
		DirectoryScanner ds = fileSet.getDirectoryScanner();
		Iterator<?> iter = new FileResourceIterator(fileSet.getDir(), ds.getIncludedFiles());
		
		while (iter.hasNext()) {
			FileResource resource = (FileResource) iter.next();
			process(resource.getFile());
		}
		
	}
	
	private void process(File file) {
		try {
			
			ZipFile zip = new ZipFile(file);
			for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
				ZipEntry entry = e.nextElement();
				if (!entry.isDirectory() && entry.getName().startsWith("META-INF/services/"))
					process(zip, entry);
			}
			
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}
	
	private void process(ZipFile zip, ZipEntry entry) throws IOException {
		File result = new File(dest, entry.getName());
		
		if (!result.getParentFile().exists() && !result.getParentFile().mkdirs())
			throw new BuildException("Could not create dir " + result.getParent());
		
		InputStream in = null;
		OutputStream out = null;
		try {
			
			in = zip.getInputStream(entry);
			out = new FileOutputStream(result, true);
			
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0)
				out.write(buf, 0, len);
			
		} finally {
			close(out);
			close(in);
		}
	}
	
	private void close(Closeable writer) {
		try {
			
			if (writer != null)
				writer.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
