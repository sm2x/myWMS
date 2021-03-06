/*
 * Copyright (c) 2006 by Fraunhofer IML, Dortmund.
 * All rights reserved.
 *
 * Project: myWMS
 */
package org.mywms.ejb;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.transaction.UserTransaction;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.logging.Logger;

/**
 * The locator creates remote instances of session beans, served by the
 * application server. 
 * 
 * @version $Revision: 741 $ provided by $Author: mkrane $
 */
public class BeanLocator implements Externalizable {
	private static final long serialVersionUID = 1L;

	private static final Logger logger = Logger.getLogger(BeanLocator.class.getName());

	private static final String JNDI_NAME_USER_TRANSACTION = "java:comp/UserTransaction";
	private static final String JNDI_NAME_CONNECTION_FACTORY = "ConnectionFactory";

	private transient InitialContext initialContext;
	private transient Map<String, Object> statelessCache = new HashMap<String, Object>();
	private transient QueueConnectionFactory connectionFactory;

	private Properties initialContextProperties;

	private Properties appServerProperties;

	private String applicationName;

	private Map<String, String> mappingHash = null;

	/**
	 * Creates a new instance of BeanLocator.
	 */
	public BeanLocator() {
		this(null);
	}

	/**
	 * Creates a new instance of BeanLocator.
	 * 
	 * @param user
	 *            the user account, used to connect to the application server
	 * @param passwd
	 *            the password of the user account
	 */
	public BeanLocator(String user, String passwd) {
		if(initialContextProperties==null) {
			initialContextProperties=new Properties();
		}
		if (user != null) {
			initialContextProperties.put(Context.SECURITY_PRINCIPAL, user);
			if (passwd != null) {
				initialContextProperties.put(Context.SECURITY_CREDENTIALS, passwd);
			}
		}
		initJNDIContext();
	}

	/**
	 * Creates a new instance of BeanLocator.
	 * 
	 * @param user
	 *            the user account, used to connect to the application server
	 * @param passwd
	 *            the password of the user account
	 */
	public BeanLocator(String user, String passwd, Properties jndiProps, Properties appServerProps) {

		if (jndiProps != null) {
			initialContextProperties = jndiProps;
		}

		if(appServerProps !=null) {
			appServerProperties = appServerProps;
		}

		mappingHash = propertiesToMapForModules(appServerProperties);

		applicationName = appServerProperties.getProperty("org.mywms.env.applicationName");

		String defaultUser = initialContextProperties.getProperty("org.mywms.env.defaultUser");
		String defaultPassword = initialContextProperties.getProperty("org.mywms.env.defaultPassword");

		// with default user
		if (defaultUser != null && defaultPassword != null && !defaultUser.isEmpty() && !defaultPassword.isEmpty()) {
			authentification(defaultUser, defaultPassword);
		}
		// with login data
		else {
			if (user != null && passwd != null) {
				authentification(user, passwd);
			} else {
				logger.error("Authentification failed, username or password is null");
			}
		}
		initJNDIContext();
	}

	/**
	 * Creates a new instance of BeanLocator.
	 * 
	 * @param initialContextProperties
	 *            the properties to use
	 */
	public BeanLocator(Properties appServerProps) {
		if (appServerProps != null) {
			appServerProperties = appServerProps;
		}
		initialContextProperties = new Properties();
		applicationName = this.appServerProperties.getProperty("org.mywms.env.applicationName");

		//		appServerProperties.put(Context.INITIAL_CONTEXT_FACTORY,
		//				"org.jboss.naming.remote.client.InitialContextFactory");

		mappingHash = propertiesToMapForModules(appServerProperties);
		//		logger.debug("initialize mappingHash: " + mappingHash);

	}

	private static Map<String, String> propertiesToMapForModules(Properties props) {
		final String SEARCH_KEY = "org.mywms.env.mapping.";
		HashMap<String, String> hm = new HashMap<String, String>();
		Enumeration<Object> e = props.keys();
		while (e.hasMoreElements()) {
			String s = (String) e.nextElement();
			if (s.startsWith(SEARCH_KEY)) {

				String[] packages = props.getProperty(s).split(",");

				for (String p : packages) {
					hm.put(p, s.replaceFirst(SEARCH_KEY, ""));
					//					logger.info("Mapping : key = " + p + " : value = " + s.replaceFirst(SEARCH_KEY, ""));
				}
			}
		}
		return hm;
	}

	private void authentification(String username, String password) {

		//		logger.info("authentification with username: " + username + " and password: " + password);

		initialContextProperties.put("remote.connections", "default");

		initialContextProperties.put("remote.connection.default.username", username);
		initialContextProperties.put("remote.connection.default.password", password);

		initialContextProperties.put("remote.connection.default.host",
				initialContextProperties.get("remote.connection.default.host"));
		initialContextProperties.put("remote.connection.default.port",
				initialContextProperties.get("remote.connection.default.port"));

		initialContextProperties.put(
				"remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS",
				initialContextProperties
				.get("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS"));
		initialContextProperties.put(
				"remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT",
				initialContextProperties
				.get("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT"));
		initialContextProperties.put(
				"remote.connection.default.connect.options.org.xnio.Options.SASL_DISALLOWED_MECHANISMS",
				initialContextProperties
				.get("remote.connection.default.connect.options.org.xnio.Options.SASL_DISALLOWED_MECHANISMS"));
		initialContextProperties.put("remote.initialContextProperties.create.options.org.xnio.Options.SSL_ENABLED",
				initialContextProperties.get("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED"));

		final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(
				initialContextProperties);

		// EJB client context selection is based on selectors. So let's create a
		// ConfigBasedEJBClientContextSelector which uses our
		// EJBClientConfiguration created in previous step
		final ContextSelector<EJBClientContext> ejbClientContextSelector = new ConfigBasedEJBClientContextSelector(
				ejbClientConfiguration);

		// Now let's setup the EJBClientContext to use this selector
		EJBClientContext.setSelector(ejbClientContextSelector);

	}

	/**
	 * Returns the initial context.
	 * 
	 * @return the initial context
	 * @throws BeanLocatorException
	 */

	private Context getInitialContext() throws BeanLocatorException {
		if (initialContext == null) {
			try {
				initialContext = new InitialContext(initialContextProperties);
			} catch (NamingException ne) {
				throw new BeanLocatorException(ne);
			} catch (Exception e) {
				throw new BeanLocatorException(e);
			}
		}
		return initialContext;
	}


	private void initJNDIContext() {
		if(initialContextProperties!=null) {
			initialContextProperties.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
			initialContextProperties.put("jboss.naming.client.ejb.context", "true");
		}
	}

	public <T> T getStateless(Class<T> interfaceClazz) {
		String logStr = "getStateless ";

		if(applicationName==null) {
			logger.error(logStr+"application name not found");
			return null;
		}


		String interfacePackage = interfaceClazz.getPackage().getName();
		String interfaceName = interfaceClazz.getName();

		String moduleName = null;
		// Try to resolve complete interface name
		for (Iterator<String> it = mappingHash.keySet().iterator(); it.hasNext();) {
			String s = it.next();
			if (interfaceName.equals(s)) {
				moduleName = mappingHash.get(s);
				//				logger.info(logStr + "moduleName: " + moduleName + ", key: " + s);
				break;
			}
		}
		// Try to resolve interface package name
		if( moduleName==null ) {
			for (Iterator<String> it = mappingHash.keySet().iterator(); it.hasNext();) {
				String s = it.next();
				if (interfacePackage.contains(s)) {
					moduleName = mappingHash.get(s);
					//					logger.info(logStr + "moduleName: " + moduleName + ", key: " + s);
					break;
				}
			}
		}
		if (moduleName == null) {
			logger.error(logStr+"no module found for interface. package="+interfacePackage+", name="+interfaceName);
			return null;
		}

		String lookUpString = resolve(interfaceClazz, applicationName, moduleName);
		return getStateless(interfaceClazz, moduleName, lookUpString);

	}

	public <T> T getStateless(Class<T> interfaceClazz, String moduleName) {
		String lookUpString = resolve(interfaceClazz, applicationName, moduleName);
		return getStateless(interfaceClazz, moduleName, lookUpString);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getStateless(Class<T> interfaceClazz, String moduleName, String lookUpString)
			throws BeanLocatorException {
		String logStr = "getStateless ";

		if (moduleName == null || moduleName.equals(null)) {
			logger.error(logStr+"No moduleName defined!");
			return null;
		}

		//		logger.info(logStr + "---- get Stateless Session Bean " + lookUpString);
		//Properties jndiProperties = new Properties();
		//jndiProperties.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
		//jndiProperties.put("jboss.naming.client.ejb.context", "true");

		try {			
			//(T) statelessCache.get(lookUpString);
			//if (stateless == null) {
			T stateless = (T) getInitialContext().lookup(lookUpString);
			//statelessCache.put(lookUpString, stateless);
			//}
			return stateless;
		} catch (NamingException ne) {
			logger.error(logStr + "Error when trying lookup: " + ne.getLocalizedMessage());
			throw new BeanLocatorException(ne);
		}

	}

	public <T> T getStateful(Class<T> interfaceClazz) {
		return getStateful(interfaceClazz, null);
	}

	@SuppressWarnings("unchecked")
	public <T> T getStateful(Class<T> interfaceClazz, String jndiName) throws BeanLocatorException {
		if (jndiName == null) {
			jndiName = interfaceClazz.getName();
		}
		try {
			T result = (T) getInitialContext().lookup(jndiName);
			return result;
		} catch (NamingException ne) {
			throw new BeanLocatorException(ne);
		}
	}

	public QueueConnectionFactory getQueueConnectionFactory() throws BeanLocatorException {
		if (connectionFactory == null) {
			try {
				Object ref = getInitialContext().lookup(JNDI_NAME_CONNECTION_FACTORY);
				connectionFactory = (QueueConnectionFactory) PortableRemoteObject.narrow(ref,
						QueueConnectionFactory.class);
			} catch (NamingException e) {
				throw new BeanLocatorException(JNDI_NAME_CONNECTION_FACTORY + " konnte nicht erzeugt werden", e);
			}
		}
		return connectionFactory;
	}

	public UserTransaction getUserTransaction() throws BeanLocatorException {
		try {
			UserTransaction ut = (UserTransaction) getInitialContext().lookup(JNDI_NAME_USER_TRANSACTION);
			return ut;
		} catch (NamingException e) {
			throw new BeanLocatorException(JNDI_NAME_USER_TRANSACTION + " konnte nicht erzeugt werden", e);
		}
	}

	public Queue getQueue(String queuename) throws BeanLocatorException {
		try {
			return (Queue) getInitialContext().lookup(queuename);
		} catch (NamingException e) {
			throw new BeanLocatorException(e);
		}
	}

	public Topic getTopic(String topicname) throws BeanLocatorException {
		try {
			return (Topic) getInitialContext().lookup(topicname);
		} catch (NamingException e) {
			throw new BeanLocatorException(e);
		}
	}

	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		statelessCache = new HashMap<String, Object>();
	}

	public void writeExternal(ObjectOutput out) throws IOException {
	}


	private String resolve(Class<?> interfaceClazz, String applicationName, String moduleName) {
		String logStr="resolve ";
		String beanName;
		String jndiName;
		beanName = interfaceClazz.getSimpleName().replaceFirst("Remote","");
		jndiName ="ejb:" + applicationName
				+ "/"+ moduleName + "//"
				+ beanName + "Bean!" + interfaceClazz.getName();
		logger.debug(logStr+ "jndiName: "+jndiName);
		System.out.println(logStr+ "jndiName: "+jndiName);
		return  jndiName;
	}


	public static BeanLocator lookupBeanLocator(String server, int port, String username, String password) {
		// if you want to load the config from a file. 
		//Properties jndi = AppPreferences.loadFromClasspath("/config/wf8-context.properties");
		//Properties appserver = AppPreferences.loadFromClasspath("/config/appserver.properties");

		Properties jndi = new Properties();
		jndi.setProperty("org.mywms.env.as.vendor", "jboss");
		jndi.setProperty("org.mywms.env.as.version", "8.2");

		jndi.setProperty("remote.connections", "default");
		jndi.setProperty("remote.connection.default.port", Integer.toString(port));
		jndi.setProperty("remote.connection.default.host", server);
		jndi.setProperty("remote.connection.default.connect.timeout","1500");

		jndi.setProperty("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS", "true");
		jndi.setProperty("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT", "false");
		jndi.setProperty("remote.connection.default.connect.options.org.xnio.Options.SASL_DISALLOWED_MECHANISMS", "JBOSS-LOCAL-USER");
		jndi.setProperty("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED", "false");

		Properties appserver = new Properties();
		appserver.setProperty("org.mywms.env.applicationName", "los.reference");
		appserver.setProperty("org.mywms.env.mapping.project-ejb3", "de.linogistix.los.reference,de.linogistix.los.common.facade.VersionFacade");
		appserver.setProperty("org.mywms.env.mapping.myWMS-comp", "org.mywms");
		appserver.setProperty("org.mywms.env.mapping.los.stocktaking-comp", "de.linogistix.los.stocktaking");
		appserver.setProperty("org.mywms.env.mapping.los.mobile-comp", "de.linogistix.mobileserver");
		appserver.setProperty("org.mywms.env.mapping.los.mobile","de.linogistix.mobile.common, de.linogistix.mobile.processes");
		appserver.setProperty("org.mywms.env.mapping.los.location-comp", "de.linogistix.los.location");
		appserver.setProperty("org.mywms.env.mapping.los.inventory-ws", "de.linogistix.los.inventory.ws");
		appserver.setProperty("org.mywms.env.mapping.los.inventory-comp", "de.linogistix.los.inventory");
		appserver.setProperty("org.mywms.env.mapping.los.common-comp", "de.linogistix.los.common,de.linogistix.los.crud,de.linogistix.los.customization,de.linogistix.los.entityservice,de.linogistix.los.query,de.linogistix.los.report,de.linogistix.los.runtime,de.linogistix.los.user,de.linogistix.los.util");

		BeanLocator b = new BeanLocator(username, password, jndi, appserver);
		return b;
	}

}