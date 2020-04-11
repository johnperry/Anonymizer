package org.rsna.anonymizer;

import java.awt.Color;
import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ui.ApplicationProperties;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;

/**
 * The singleton class that encapsulates the configuration of the program.
 */
public class Configuration {

	static final Logger logger = Logger.getLogger(Configuration.class);

    static final String propsFile 		= "program.properties";
    static final String dicomScriptFile	= "dicom-anonymizer.script";
    static final String lookupTableFile	= "lookup-table.properties";
    static final String helpfile 		= "help.html";
    
    IntegerTable integerTable = null;
    File storageDir = null;
    File databaseDir = null;

	public static final Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	static Configuration configuration = null;
    private ApplicationProperties props;

	/**
	 * Get the singleton instance of the Configuration.
	 * @return the Configuration.
	 */
	public static synchronized Configuration getInstance() {
		if (configuration == null) configuration = new Configuration();
		return configuration;
	}

	//The protected constructor.
	protected Configuration() {
		logger.info("\nAvailable Codecs:\n"+org.rsna.util.ImageIOTools.listAvailableCodecs());
		props = new ApplicationProperties(new File(propsFile));
		File home = new File(System.getProperty("user.dir"));
		databaseDir = new File(home, "data");
		databaseDir.mkdirs();
		try { 
			integerTable = new IntegerTable(databaseDir);
			storageDir = home;
			String storageDirProp = props.getProperty("storageDir", "Storage");
			if (storageDirProp != null) storageDir = new File(storageDirProp);
			props.setProperty("storageDir", storageDir.getAbsolutePath());
			String ext = props.getProperty("extensions", ".dcm,[dcm]");
			props.setProperty("extensions", ext);
			props.store();
		}
		catch (Exception ex) { }
	}
	
	public IntegerTable getIntegerTable() {
		return integerTable;
	}
	
	public File getStorageDir() {
		return storageDir;
	}

	public File getDatabaseDir() {
		return databaseDir;
	}

	public void setStorageDir(File storageDir) {
		props.put("storageDir", storageDir.getAbsolutePath());
		this.storageDir = storageDir;
	}

	public ApplicationProperties getProps() {
		return props;
	}

	public void put(String key, String value) {
		props.setProperty(key, value);
	}

	public String get(String key) {
		return props.getProperty(key);
	}

	public void store() {
		props.store();
	}

}
