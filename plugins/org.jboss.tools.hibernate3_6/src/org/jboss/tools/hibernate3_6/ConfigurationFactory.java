/*******************************************************************************
 * Copyright (c) 2012 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.hibernate3_6;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.DOMWriter;
import org.eclipse.datatools.connectivity.IConnectionProfile;
import org.eclipse.datatools.connectivity.ProfileManager;
import org.eclipse.osgi.util.NLS;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.annotations.common.util.ReflectHelper;
import org.hibernate.annotations.common.util.StringHelper;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.NamingStrategy;
import org.hibernate.connection.DriverManagerConnectionProvider;
import org.hibernate.console.ConnectionProfileUtil;
import org.hibernate.console.ConsoleConfiguration;
import org.hibernate.console.ConsoleMessages;
import org.hibernate.console.HibernateConsoleRuntimeException;
import org.hibernate.console.preferences.ConsoleConfigurationPreferences;
import org.hibernate.console.preferences.ConsoleConfigurationPreferences.ConfigurationMode;
import org.hibernate.util.ConfigHelper;
import org.hibernate.util.XMLHelper;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

public class ConfigurationFactory {

	public static final String FAKE_TM_LOOKUP = "org.hibernate.console.FakeTransactionManagerLookup"; //$NON-NLS-1$
	
	private ConsoleConfigurationPreferences prefs;
	private Map<String, FakeDelegatingDriver> fakeDrivers;

	public ConfigurationFactory(ConsoleConfigurationPreferences prefs,
			Map<String, FakeDelegatingDriver> fakeDrivers) {
		this.prefs = prefs;
		this.fakeDrivers = fakeDrivers;
	}

	public ConsoleConfigurationPreferences getPreferences() {
		return prefs;
	}
	
	private void changeDatasourceProperties(Configuration localCfg){
		final Properties invokeProperties = localCfg.getProperties();
		// set this property to null!
		if (invokeProperties.containsKey(Environment.DATASOURCE)){
			invokeProperties.setProperty(Environment.TRANSACTION_MANAGER_STRATEGY, FAKE_TM_LOOKUP);
			invokeProperties.put(Environment.CONNECTION_PROVIDER, DriverManagerConnectionProvider.class.getName());
			invokeProperties.remove(Environment.DATASOURCE);
			localCfg.setProperties(invokeProperties);
		}
	}

	public Configuration createConfiguration(Configuration localCfg, boolean includeMappings) {
		Properties properties = prefs.getProperties();

		if (properties != null) {
			// in case the transaction manager is empty then we need to inject a faketm since
			// hibernate will still try and instantiate it.
			String str = properties.getProperty(Environment.TRANSACTION_MANAGER_STRATEGY);
			if (str != null && StringHelper.isEmpty(str)) {
				properties.setProperty(Environment.TRANSACTION_MANAGER_STRATEGY, FAKE_TM_LOOKUP);
				// properties.setProperty( "hibernate.transaction.factory_class", "");
			}
		}
		if (localCfg == null) {
			localCfg = buildConfiguration(properties, includeMappings);
		} else {
			// Properties origProperties = cfg.getProperties();
			// origProperties.putAll(properties);
			// cfg.setProperties(origProperties);
			// TODO: this is actually only for jdbc reveng...
			// localCfg = configureStandardConfiguration( includeMappings, localCfg, properties );
		}

		// here both setProperties and configxml have had their chance to tell which databasedriver
		// is needed.
		registerFakeDriver(localCfg.getProperty(Environment.DRIVER));
		// autoConfigureDialect(localCfg); Disabled for now since it causes very looong timeouts for
		// non-running databases + i havent been needed until now...

		// TODO: jpa configuration ?
		if (includeMappings) {
			File[] mappingFiles = prefs.getMappingFiles();

			for (int i = 0; i < mappingFiles.length; i++) {
				File hbm = mappingFiles[i];
				localCfg = localCfg.addFile(hbm);
			}
		}
		// TODO: HBX-
		localCfg.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false"); //$NON-NLS-1$//$NON-NLS-2$
		localCfg.setProperty(Environment.HBM2DDL_AUTO, "false"); //$NON-NLS-1$
		// to fix: JBIDE-5839 & JBIDE-5997 - setup this property: false is default value
		// to make hibernate tools diff hibernate versions compatible:
		// if the property not set get NoSuchMethodError with FullTextIndexEventListener
		if (localCfg.getProperty("hibernate.search.autoregister_listeners") == null) { //$NON-NLS-1$
			localCfg.setProperty("hibernate.search.autoregister_listeners", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return localCfg;
	}

	@SuppressWarnings("unused")
	private void autoConfigureDialect(Configuration localCfg) {
		if (localCfg.getProperty(Environment.DIALECT) == null) {
			String dialect = ConnectionProfileUtil.autoDetectDialect(localCfg.getProperties());
			if (dialect != null){
				localCfg.setProperty(Environment.DIALECT, dialect);
			}
		}
	}

	// TODO: delegate to some extension point
	private Configuration buildConfiguration(Properties properties, boolean includeMappings) {
		Configuration localCfg = null;
		if (prefs.getConfigurationMode().equals(ConfigurationMode.ANNOTATIONS)) {
			try {
				localCfg = buildAnnotationConfiguration();
				localCfg = configureStandardConfiguration(includeMappings, localCfg, properties);
			} catch (HibernateConsoleRuntimeException he) {
				throw he;
			} catch (Exception e) {
				throw new HibernateConsoleRuntimeException(
						ConsoleMessages.ConsoleConfiguration_could_not_load_annotationconfiguration,
						e);
			}
		} else if (prefs.getConfigurationMode().equals(ConfigurationMode.JPA)) {
			try {
				localCfg = buildJPAConfiguration(getPreferences().getPersistenceUnitName(),
						properties, prefs.getEntityResolverName(), includeMappings);
			} catch (HibernateConsoleRuntimeException he) {
				throw he;
			} catch (Exception e) {
				throw new HibernateConsoleRuntimeException(
						ConsoleMessages.ConsoleConfiguration_could_not_load_jpa_configuration, e);
			}
		} else {
			localCfg = new Configuration();
			localCfg = configureStandardConfiguration(includeMappings, localCfg, properties);
		}
		return localCfg;
	}

	@SuppressWarnings("unchecked")
	private Configuration buildAnnotationConfiguration() throws ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		Class<Configuration> clazz = ReflectHelper
				.classForName("org.hibernate.cfg.AnnotationConfiguration"); //$NON-NLS-1$
		Configuration newInstance = clazz.newInstance();
		return newInstance;
	}

	private Configuration buildJPAConfiguration(String persistenceUnit, Properties properties,
			String entityResolver, boolean includeMappings) {
		if (StringHelper.isEmpty(persistenceUnit)) {
			persistenceUnit = null;
		}
		try {
			Map<Object, Object> overrides = new HashMap<Object, Object>();
			if (properties != null) {
				overrides.putAll(properties);
			}
			if (StringHelper.isNotEmpty(prefs.getNamingStrategy())) {
				overrides.put("hibernate.ejb.naming_strategy", prefs.getNamingStrategy()); //$NON-NLS-1$
			}
			if (StringHelper.isNotEmpty(prefs.getDialectName())) {
				overrides.put(Environment.DIALECT, prefs.getDialectName());
			}
			if (!includeMappings) {
				overrides.put("hibernate.archive.autodetection", "none"); //$NON-NLS-1$//$NON-NLS-2$
			}
			if (StringHelper.isEmpty((String) overrides.get("javax.persistence.validation.mode"))) {//$NON-NLS-1$
				overrides.put("javax.persistence.validation.mode", "none"); //$NON-NLS-1$//$NON-NLS-2$
			}
			Class<?> clazz = ReflectHelper.classForName(
					"org.hibernate.ejb.Ejb3Configuration", ConsoleConfiguration.class); //$NON-NLS-1$
			Object ejb3cfg = clazz.newInstance();
			if (StringHelper.isNotEmpty(entityResolver)) {
				Class<?> resolver = ReflectHelper.classForName(entityResolver, this.getClass());
				Object object = resolver.newInstance();
				Method method = clazz.getMethod(
						"setEntityResolver", new Class[] { EntityResolver.class });//$NON-NLS-1$
				method.invoke(ejb3cfg, new Object[] { object });
			}
			Method method = clazz.getMethod("configure", new Class[] { String.class, Map.class }); //$NON-NLS-1$
			if (method.invoke(ejb3cfg, new Object[] { persistenceUnit, overrides }) == null) {
				String out = NLS.bind(
						ConsoleMessages.ConsoleConfiguration_persistence_unit_not_found,
						persistenceUnit);
				throw new HibernateConsoleRuntimeException(out);
			}
			method = clazz.getMethod("getHibernateConfiguration", new Class[0]);//$NON-NLS-1$
			Configuration invoke = (Configuration) method.invoke(ejb3cfg, (Object[]) null);
			changeDatasourceProperties(invoke);
			invoke = configureConnectionProfile(invoke);
			return invoke;
		} catch (HibernateConsoleRuntimeException he) {
			throw he;
		} catch (Exception e) {
			throw new HibernateConsoleRuntimeException(
					ConsoleMessages.ConsoleConfiguration_could_not_create_jpa_based_configuration,
					e);
		}
	}

	private Configuration configureStandardConfiguration(final boolean includeMappings,
			Configuration localCfg, Properties properties) {
		if (properties != null) {
			localCfg = localCfg.setProperties(properties);
		}
		EntityResolver entityResolver = XMLHelper.DEFAULT_DTD_RESOLVER;
		if (StringHelper.isNotEmpty(prefs.getEntityResolverName())) {
			try {
				entityResolver = (EntityResolver) ReflectHelper.classForName(
						prefs.getEntityResolverName()).newInstance();
			} catch (Exception c) {
				throw new HibernateConsoleRuntimeException(
						ConsoleMessages.ConsoleConfiguration_could_not_configure_entity_resolver
								+ prefs.getEntityResolverName(), c);
			}
		}
		localCfg.setEntityResolver(entityResolver);
		if (StringHelper.isNotEmpty(prefs.getNamingStrategy())) {
			try {
				NamingStrategy ns = (NamingStrategy) ReflectHelper.classForName(
						prefs.getNamingStrategy()).newInstance();
				localCfg.setNamingStrategy(ns);
			} catch (Exception c) {
				throw new HibernateConsoleRuntimeException(
						ConsoleMessages.ConsoleConfiguration_could_not_configure_naming_strategy
								+ prefs.getNamingStrategy(), c);
			}
		}
		localCfg = loadConfigurationXML(localCfg, includeMappings, entityResolver);
		changeDatasourceProperties(localCfg);
		localCfg = configureConnectionProfile(localCfg);
		// replace dialect if it is set in preferences
		if (StringHelper.isNotEmpty(prefs.getDialectName())) {
			localCfg.setProperty(Environment.DIALECT, prefs.getDialectName());
		}
		if (StringHelper.isEmpty(localCfg.getProperty("javax.persistence.validation.mode"))) {//$NON-NLS-1$
			localCfg.setProperty("javax.persistence.validation.mode", "none"); //$NON-NLS-1$//$NON-NLS-2$
		}
		return localCfg;
	}

	@SuppressWarnings("unchecked")
	private Configuration loadConfigurationXML(Configuration localCfg, boolean includeMappings,
			EntityResolver entityResolver) {
		File configXMLFile = prefs.getConfigXMLFile();
		if (!includeMappings) {
			org.dom4j.Document doc;
			XMLHelper xmlHelper = new XMLHelper();
			InputStream stream = null;
			String resourceName = "<unknown>"; //$NON-NLS-1$
			if (configXMLFile != null) {
				resourceName = configXMLFile.toString();
				try {
					stream = new FileInputStream(configXMLFile);
				} catch (FileNotFoundException e1) {
					throw new HibernateConsoleRuntimeException(
							ConsoleMessages.ConsoleConfiguration_could_not_access + configXMLFile,
							e1);
				}
			} else {
				resourceName = "/hibernate.cfg.xml"; //$NON-NLS-1$
				if (checkHibernateResoureExistence(resourceName)) {
					stream = ConfigHelper.getResourceAsStream(resourceName); // simulate hibernate's
																				// default look up
				} else {
					return localCfg;
				}
			}
			try {
				List<Throwable> errors = new ArrayList<Throwable>();
				doc = xmlHelper.createSAXReader(resourceName, errors, entityResolver).read(
						new InputSource(stream));
				if (errors.size() != 0) {
					throw new MappingException(
							ConsoleMessages.ConsoleConfiguration_invalid_configuration, errors
									.get(0));
				}
				List<Node> list = doc.getRootElement()
						.element("session-factory").elements("mapping"); //$NON-NLS-1$ //$NON-NLS-2$
				for (Node element : list) {
					element.getParent().remove(element);
				}
				DOMWriter dw = new DOMWriter();
				Document document = dw.write(doc);
				return localCfg.configure(document);

			} catch (DocumentException e) {
				throw new HibernateException(
						ConsoleMessages.ConsoleConfiguration_could_not_parse_configuration
								+ resourceName, e);
			} finally {
				try {
					if (stream != null)
						stream.close();
				} catch (IOException ioe) {
					// log.warn( "could not close input stream for: " + resourceName, ioe );
				}
			}
		} else {
			if (configXMLFile != null) {
				return localCfg.configure(configXMLFile);
			} else {
				Configuration resultCfg = localCfg;
				if (checkHibernateResoureExistence("/hibernate.cfg.xml")) { //$NON-NLS-1$
					resultCfg = localCfg.configure();
				}
				return resultCfg;
			}
		}
	}

	private boolean checkHibernateResoureExistence(String resource) {
		InputStream is = null;
		try {
			is = ConfigHelper.getResourceAsStream(resource);
		} catch (HibernateException e) {
			// just ignore
		} finally {
			try {
				if (is != null)
					is.close();
			} catch (IOException e) {
				// ignore
			}
		}
		return (is != null);
	}

	private Configuration configureConnectionProfile(Configuration localCfg) {
		String connProfileName = prefs.getConnectionProfileName();
		if (connProfileName == null) {
			return localCfg;
		}
		IConnectionProfile profile = ProfileManager.getInstance().getProfileByName(
				connProfileName);
		if (profile != null) {
			localCfg.addProperties(ConnectionProfileUtil.getHibernateConnectionProperties(profile));
		} else {
			String out = NLS.bind(
					ConsoleMessages.ConsoleConfiguration_connection_profile_not_found,
					connProfileName);
			throw new HibernateConsoleRuntimeException(out);
		}
		return localCfg;
	}

	/**
	 * DriverManager checks what classloader a class is loaded from thus we register a FakeDriver
	 * that we know is loaded "properly" which delegates all it class to the real driver. By doing
	 * so we can convince DriverManager that we can use any dynamically loaded driver.
	 * 
	 * @param driverClassName
	 */
	@SuppressWarnings("unchecked")
	private void registerFakeDriver(String driverClassName) {
		if (driverClassName != null) {
			try {
				Class<Driver> driverClass = ReflectHelper.classForName(driverClassName);
				if (!fakeDrivers.containsKey(driverClassName)) { // To avoid "double registration"
					FakeDelegatingDriver fakeDelegatingDriver = new FakeDelegatingDriver(
							driverClass.newInstance());
					DriverManager.registerDriver(fakeDelegatingDriver);
					fakeDrivers.put(driverClassName, fakeDelegatingDriver);
				}
			} catch (ClassNotFoundException e) {
				String out = 
					NLS.bind(ConsoleMessages.ConsoleConfiguration_problems_while_loading_database_driverclass, driverClassName);
				throw new HibernateConsoleRuntimeException(out, e);
			} catch (InstantiationException e) {
				String out = 
					NLS.bind(ConsoleMessages.ConsoleConfiguration_problems_while_loading_database_driverclass, driverClassName);
				throw new HibernateConsoleRuntimeException(out, e);
			} catch (IllegalAccessException e) {
				String out = 
					NLS.bind(ConsoleMessages.ConsoleConfiguration_problems_while_loading_database_driverclass, driverClassName);
				throw new HibernateConsoleRuntimeException(out, e);
			} catch (SQLException e) {
				String out = 
					NLS.bind(ConsoleMessages.ConsoleConfiguration_problems_while_loading_database_driverclass, driverClassName);
				throw new HibernateConsoleRuntimeException(out, e);
			}
		}
	}

}