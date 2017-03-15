package org.pescuma.mergeservices;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

//TODO-3.1 xmlns="http://xmlns.jcp.org/xml/ns/javaee" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-fragment_3_1.xsd"
//TODO-3.0 xmlns="http://java.sun.com/xml/ns/javaee"  xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-fragment_3_0.xsd"
//TODO: xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"

@XmlRootElement(name="web-fragment", namespace=WebFragment.NS)
@XmlType(name="web-fragment", namespace=WebFragment.NS)
public class WebFragment {
	public static final String NS = "http://xmlns.jcp.org/xml/ns/javaee";
	public static class Distributable { }
	static interface Unique { String id(); }
	public static class Parameter      implements Unique {
		@XmlElement(namespace=WebFragment.NS, name="param-name" ) public String name;
		@XmlElement(namespace=WebFragment.NS, name="param-value") public String value;
		@Override public String id() { return this.name; }
	}
	public static class Listener       implements Unique {
		@XmlElement(namespace=WebFragment.NS, name="listener-class") public String clazz;
		@Override public String id() { return this.clazz; }
	}
	public static class Filter         implements Unique {
		@XmlElement(namespace=WebFragment.NS, name="filter-name" ) public String name;
		@XmlElement(namespace=WebFragment.NS, name="filter-class") public String clazz;
		@Override public String id() { return this.name; }
	}
	public static class FilterMapping  implements Unique {
		@XmlElement(namespace=WebFragment.NS, name="filter-name") public String name;
		/** Check for Duplicates */
		@XmlElement(namespace=WebFragment.NS, name="url-pattern") public String[] pattern;
		@Override public String id() { return this.name; }
	}
	public static class Servlet        implements Unique {
		@XmlElement(namespace=WebFragment.NS, name="servlet-name"   ) public String  name;
		@XmlElement(namespace=WebFragment.NS, name="servlet-class"  ) public String  clazz;
		@XmlElement(namespace=WebFragment.NS, name="load-on-startup") public Integer startup;
		@XmlElement(namespace=WebFragment.NS, name="enabled"        ) public Boolean enabled;
		@Override public String id() { return this.name; }
	}
	public static class ServletMapping implements Unique {
		@XmlElement(namespace=WebFragment.NS, name="servlet-name") public String name;
		/** Check for Duplicates */
		@XmlElement(namespace=WebFragment.NS, name="url-pattern") public String[] pattern;
		@Override public String id() { return this.name; }
	}

	/** DEFAULT: true if any from merge is false than result will be false */
	@XmlAttribute(namespace=WebFragment.NS, name="metadata-complete") public final boolean             metadataComplete = false;
	@XmlAttribute(namespace=WebFragment.NS, name="id")                public final String              id_;
	@XmlAttribute(namespace=WebFragment.NS, name="version")           public double              version          = 3.1 ;		// OR HIGHER
	@XmlElement(namespace=WebFragment.NS, name="name")            public final String              name_;
	@XmlElement(namespace=WebFragment.NS, name="distributable")   public Distributable       distributable    = null;
	@XmlElement(namespace=WebFragment.NS, name="context-param")   public List<Parameter     > contextParam     = new LinkedList<>();
	@XmlElement(namespace=WebFragment.NS, name="listener")	      public List<Listener      > listener         = new LinkedList<>();
	@XmlElement(namespace=WebFragment.NS, name="filter")		  public List<Filter        > filter           = new LinkedList<>();
	@XmlElement(namespace=WebFragment.NS, name="filter-mapping")  public List<FilterMapping > filterMapping    = new LinkedList<>();
	@XmlElement(namespace=WebFragment.NS, name="servlet")		  public List<Servlet       > servlet          = new LinkedList<>();
	@XmlElement(namespace=WebFragment.NS, name="servlet-mapping") public List<ServletMapping> servletMapping   = new LinkedList<>();

	@Deprecated public WebFragment() { this.id_=this.name_="XML-Loaded"; }
	public WebFragment(final String name) { this.id_=this.name_=name; }

	transient private Map<String, List<String>> c_param_ids  = new HashMap<>();
	transient private Map<String, List<String>> filter_ids   = new HashMap<>();
	transient private Map<String, List<String>> listener_ids = new HashMap<>();
	transient private Map<String, List<String>> flt_map_ids  = new HashMap<>();
	transient private Map<String, List<String>> servlet_ids  = new HashMap<>();
	transient private Map<String, List<String>> srv_map_ids  = new HashMap<>();

	private <V extends Unique> void checkUnique(final List<V> my_ids, final String typ, final Map<String, List<String>> definitions, final List<V> new_ids, final String location) {
		if(new_ids != null) for(final V u : new_ids) {
			final String key = u.id();
			List<String> twice =  definitions.get(key);
			if(twice == null) definitions.put(key, twice = new LinkedList<>());
			twice.add(location);
			if(twice.size()>1) { System.out.println("web-fragment["+typ+"] with key["+key+"] defined at "+twice); }
			my_ids.add(u);
		}
	}

	public void merge(final WebFragment model, final String location) {
		if(model.version > this.version) this.version       = model.version;
		if(model.distributable != null ) this.distributable = model.distributable;
		checkUnique(this.contextParam  , "TYP", this.c_param_ids , model.contextParam  , location);
		checkUnique(this.listener      , "TYP", this.listener_ids, model.listener      , location);
		checkUnique(this.filter        , "TYP", this.filter_ids  , model.filter        , location);
		checkUnique(this.filterMapping , "TYP", this.flt_map_ids , model.filterMapping , location);
		checkUnique(this.servlet       , "TYP", this.servlet_ids , model.servlet       , location);
		checkUnique(this.servletMapping, "TYP", this.srv_map_ids , model.servletMapping, location);
	}

	transient private JAXBContext  c = null;
	transient private Unmarshaller u = null;
	transient private Marshaller   m = null;

	public void merge(final InputStream s, final String location) {
		try {
			if(this.c == null) this.c = JAXBContext.newInstance(WebFragment.class);
			if(this.u == null) this.u = this.c.createUnmarshaller();
			final WebFragment other = WebFragment.class.cast(this.u.unmarshal(s));
			merge(other, location);
		} catch(final Exception e) {
			final IllegalArgumentException x = new IllegalArgumentException("merge(.,"+location+")=>"+e.getMessage(), e);
			throw x;
		}
	}

	public void store(final OutputStream s) {
		try {
			if(this.c == null) this.c = JAXBContext.newInstance(WebFragment.class);
			if(this.m == null) {
				this.m = this.c.createMarshaller();
				this.m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			}
			this.m.marshal(this, s);
		} catch(final Exception e) {
			final IllegalArgumentException x = new IllegalArgumentException("store(.)=>"+e.getMessage(), e);
			throw x;
		}
	}
}