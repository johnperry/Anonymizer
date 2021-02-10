/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.SourcePanel;
import org.rsna.util.BrowserUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.ImageIOTools;
import org.rsna.util.JarUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The Anonymizer program base class.
 */
public class Anonymizer extends JFrame {
	
    private String					windowTitle = "RSNA Anonymizer - version 5";
    private MainPanel				mainPanel;
    private JPanel					splitPanel;
    private WelcomePanel			welcomePanel;
    private SCUPanel				scuPanel;
    private SCPPanel				scpPanel;
    private SourcePanel				sourcePanel;
    private RightPanel				rightPanel;
    private Viewer 					viewerPanel;
    private Editor 					editorPanel;
    private FilterPanel				filterPanel;
    private AnonymizerPanel			anonymizerPanel;
    private ExportPanel				exportPanel;
    private IndexPanel				indexPanel;
    private HtmlJPanel 				helpPanel;
    private LogPanel				logPanel;
    private AdminPanel				adminPanel;

	static Logger logger;

	/**
	 * The main method to start the program.
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		new Anonymizer();
    }

	/**
	 * Class constructor; creates the program main class.
	 */
    public Anonymizer() {
		super();
		
		//Initialize Log4J
		File logs = new File("logs");
		logs.mkdirs();
		for (File f : logs.listFiles()) FileUtil.deleteAll(f);
		File logProps = new File("log4j.properties");
		String propsPath = logProps.getAbsolutePath();
		if (!logProps.exists()) {
			System.out.println("Logger configuration file: "+propsPath);
			System.out.println("Logger configuration file not found.");
		}
		PropertyConfigurator.configure(propsPath);
		logger = Logger.getLogger(Anonymizer.class);
		logPanel = LogPanel.getInstance();

		Configuration config = Configuration.getInstance();
		
		//Initialize the SITEID and export protocol
		String propsSITEID = config.getProps().getProperty("SITEID");
		if (propsSITEID == null) {
			long t = System.currentTimeMillis()/60L;
			propsSITEID = Long.toString(t);
			propsSITEID = propsSITEID.substring(propsSITEID.length() - 6);
			config.getProps().setProperty("SITEID", propsSITEID);
			//Force protocol on new installations
			config.getProps().setProperty("exportProtocol", "http");
			config.store();
		}
		else {
			//On restart, if no protocol, set it to dicom
			if (config.getProps().getProperty("exportProtocol", "").equals("")) {
				config.getProps().setProperty("exportProtocol", "dicom");
				config.store();
			}
		}
		File daScriptFile = new File(config.dicomScriptFile);
		DAScript daScript = DAScript.getInstance(daScriptFile);
		Document daDoc = daScript.toXML();
		Element daRoot = daDoc.getDocumentElement();
		Node child = daRoot.getFirstChild();
		Element e = null;
		while (child != null) {
			if (child.getNodeName().equals("p")) {
				e = (Element)child;
				if (e.getAttribute("t").equals("SITEID")) break;
			}
			child = child.getNextSibling();
		}
		String scriptSITEID = e.getTextContent().trim();
		if (!scriptSITEID.equals(propsSITEID)) {
			e.setTextContent(propsSITEID);
			FileUtil.setText(daScriptFile, daScript.toXMLString());
		}
		
		//Put the build date/time in the window title
		try {
			File program = new File("Anonymizer.jar");
			String date = JarUtil.getManifestAttributes(program).get("Date");
			windowTitle += " - " + date;
		}
		catch (Exception ignore) { }
		
		setTitle(windowTitle);
		addWindowListener(new WindowCloser(this));
		mainPanel = new MainPanel();
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		
		welcomePanel = WelcomePanel.getInstance();
		scuPanel = SCUPanel.getInstance();
		scpPanel = SCPPanel.getInstance();
		sourcePanel = new SourcePanel(config.getProps(), "Directory", config.background);
		rightPanel = new RightPanel(sourcePanel);
		JSplitPane jSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePanel, rightPanel);
		jSplitPane.setResizeWeight(0.5D);
		jSplitPane.setContinuousLayout(true);
		splitPanel = new JPanel(new BorderLayout());
		splitPanel.add(jSplitPane,BorderLayout.CENTER);
		
		anonymizerPanel = new AnonymizerPanel();
		viewerPanel = new Viewer();
		editorPanel = new Editor();
		filterPanel = FilterPanel.getInstance();
		exportPanel = ExportPanel.getInstance();
		indexPanel = new IndexPanel();
		helpPanel = new HtmlJPanel( FileUtil.getText( new File(config.helpfile) ) );
		adminPanel = new AdminPanel();
		
		adminPanel.addTabs(
			viewerPanel,
			editorPanel,
			filterPanel,
			anonymizerPanel,
			indexPanel,
			logPanel);
		
		mainPanel.addTabs(
			welcomePanel,
			scuPanel,
			scpPanel,
			splitPanel,
			exportPanel,
			adminPanel,
			helpPanel);
		
		sourcePanel.addFileListener(viewerPanel);
		sourcePanel.addFileListener(editorPanel);
		pack();
		positionFrame();
		setVisible(true);
		logger.info("Initialization complete");
    }
    
	class MainPanel extends JPanel implements ChangeListener {
		public JTabbedPane tabbedPane;
		public MainPanel() {
			super();
			this.setLayout(new BorderLayout());
			setBackground(Configuration.getInstance().background);
			tabbedPane = new JTabbedPane();
			tabbedPane.addChangeListener(this);
			this.add(tabbedPane,BorderLayout.CENTER);
			
		}
		public void addTabs(
						 WelcomePanel wp,
						 SCUPanel scu,
						 SCPPanel scp,
						 JPanel source,
						 ExportPanel export,
						 AdminPanel admin,
						 JPanel help) {
			tabbedPane.addTab("Welcome", wp);
			tabbedPane.addTab("Q/R SCU", scu);
			tabbedPane.addTab("Storage SCP", scp);
			tabbedPane.addTab("Directory", source);
			tabbedPane.addTab("Export", export);
			tabbedPane.addTab("Admin", admin);
			tabbedPane.addTab("Help", help);
			tabbedPane.setSelectedIndex(0);
		}
		public void stateChanged(ChangeEvent event) {
			Component comp = tabbedPane.getSelectedComponent();
			if (comp.equals(scuPanel)) scuPanel.setFocus();
			else if (comp.equals(adminPanel)) adminPanel.stateChanged(event);
		}
	}

	class AdminPanel extends JPanel implements ChangeListener {
		public JTabbedPane tabbedPane;
		public AdminPanel() {
			super();
			this.setLayout(new BorderLayout());
			setBackground(Configuration.getInstance().background);
			tabbedPane = new JTabbedPane();
			this.add(tabbedPane,BorderLayout.CENTER);
		}
		public void addTabs(
						 Viewer viewer,
						 Editor editor,
						 FilterPanel filter,
						 AnonymizerPanel script,
						 IndexPanel index,
						 LogPanel logPanel) {
			tabbedPane.addTab("Viewer", viewer);
			tabbedPane.addTab("Elements", editor);
			tabbedPane.addTab("Filter", filter);
			tabbedPane.addTab("Script", script);
			tabbedPane.addTab("Index", index);
			tabbedPane.addTab("Log", logPanel);
			tabbedPane.addChangeListener(this);
			tabbedPane.setSelectedIndex(4);
		}
		public void stateChanged(ChangeEvent event) {
			Component comp = tabbedPane.getSelectedComponent();
			if (comp.equals(indexPanel)) indexPanel.setFocus();
			else if (comp.equals(filterPanel)) filterPanel.setFocus();
			else if (comp.equals(logPanel)) logPanel.reload();
		}
	}

    class WindowCloser extends WindowAdapter {
		JFrame parent;
		public WindowCloser(JFrame parent) {
			this.parent = parent;
		}
		public void windowClosing(WindowEvent evt) {
			Configuration config = Configuration.getInstance();
			config.getIntegerTable().close();
			Index.getInstance().close();
			SCPPanel.getInstance().shutdown();
			Point p = getLocation();
			config.put("x", Integer.toString(p.x));
			config.put("y", Integer.toString(p.y));
			Toolkit t = getToolkit();
			Dimension d = parent.getSize ();
			config.put("w", Integer.toString(d.width));
			config.put("h", Integer.toString(d.height));
			config.put("subdirectories", (sourcePanel.getSubdirectories()?"yes":"no"));
			config.store();
			System.exit(0);
		}
    }

	private void positionFrame() {
		Configuration config = Configuration.getInstance();
		int x = StringUtil.getInt( config.get("x"), 0 );
		int y = StringUtil.getInt( config.get("y"), 0 );
		int w = StringUtil.getInt( config.get("w"), 0 );
		int h = StringUtil.getInt( config.get("h"), 0 );
		boolean noProps = ((w == 0) || (h == 0));
		int wmin = 800;
		int hmin = 600;
		if ((w < wmin) || (h < hmin)) {
			w = wmin;
			h = hmin;
		}
		if ( noProps || !screensCanShow(x, y) || !screensCanShow(x+w-1, y+h-1) ) {
			Toolkit t = getToolkit();
			Dimension scr = t.getScreenSize ();
			x = (scr.width - wmin)/2;
			y = (scr.height - hmin)/2;
			w = wmin;
			h = hmin;
		}
		setSize( w, h );
		setLocation( x, y );
	}

	private boolean screensCanShow(int x, int y) {
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = env.getScreenDevices();
		for (GraphicsDevice screen : screens) {
			GraphicsConfiguration[] configs = screen.getConfigurations();
			for (GraphicsConfiguration gc : configs) {
				if (gc.getBounds().contains(x, y)) return true;
			}
		}
		return false;
	}
	
	private void checkImageIOTools() {
		String javaHome = System.getProperty("java.home");
		File extDir = new File(javaHome);
		extDir = new File(extDir, "lib");
		extDir = new File(extDir, "ext");
		File clib = FileUtil.getFile(extDir, "clibwrapper_jiio", ".jar");
		File jai = FileUtil.getFile(extDir, "jai_imageio", ".jar");
		boolean imageIOTools = (clib != null) && (jai != null);
		if (!imageIOTools) {
			JOptionPane.showMessageDialog(this, 
				"The ImageIOTools are not installed on this machine.\n" +
				"When you close this dialog, your browser will launch\n" +
				"and take you to a site where you can obtain them.");
			BrowserUtil.openURL(
				"http://mircwiki.rsna.org/index.php?title=Java_Advanced_Imaging_ImageIO_Tools");
		}			
	}

}
