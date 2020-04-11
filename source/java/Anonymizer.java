package org.rsna.anonymizer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.SourcePanel;
import org.rsna.util.BrowserUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.ImageIOTools;
import org.rsna.util.StringUtil;

/**
 * The Anonymizer program base class.
 */
public class Anonymizer extends JFrame implements ChangeListener {

    private String					windowTitle = "RSNA Anonymizer - version 3";
    private MainPanel				mainPanel;
    private JPanel					splitPanel;
    private SCUPanel				scuPanel;
    private SCPPanel				scpPanel;
    private SourcePanel				sourcePanel;
    private RightPanel				rightPanel;
    private Viewer 					viewerPanel;
    private Editor 					editorPanel;
    private FilterPanel				filterPanel;
    private AnonymizerPanel			anonymizerPanel;
    private IndexPanel				indexPanel;
    private HtmlJPanel 				helpPanel;
    private LogPanel				logPanel;

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
		setTitle(windowTitle);
		addWindowListener(new WindowCloser(this));
		mainPanel = new MainPanel();
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		
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
		indexPanel = new IndexPanel();
		helpPanel = new HtmlJPanel( FileUtil.getText( new File(config.helpfile) ) );
		
		mainPanel.addTabs(
			scuPanel,
			scpPanel,
			splitPanel,
			viewerPanel,
			editorPanel,
			filterPanel,
			anonymizerPanel,
			indexPanel,
			logPanel,
			helpPanel);
		
		mainPanel.tabbedPane.addChangeListener(this);
		sourcePanel.addFileListener(viewerPanel);
		sourcePanel.addFileListener(editorPanel);
		pack();
		positionFrame();
		setVisible(true);
		System.out.println("Initialization complete");
    }
    
	public void stateChanged(ChangeEvent event) {
		Component comp = mainPanel.tabbedPane.getSelectedComponent();
		if (comp.equals(indexPanel)) indexPanel.setFocus();
		else if (comp.equals(filterPanel)) filterPanel.setFocus();
		else if (comp.equals(logPanel)) logPanel.reload();
	}
	
	class MainPanel extends JPanel {
		public JTabbedPane tabbedPane;
		public MainPanel() {
			super();
			this.setLayout(new BorderLayout());
			setBackground(Configuration.getInstance().background);
			tabbedPane = new JTabbedPane();
			this.add(tabbedPane,BorderLayout.CENTER);
		}
		public void addTabs(
						 SCUPanel scu,
						 SCPPanel scp,
						 JPanel source,
						 Viewer viewer,
						 Editor editor,
						 FilterPanel filter,
						 AnonymizerPanel script,
						 IndexPanel index,
						 LogPanel logPanel,
						 JPanel help) {
			tabbedPane.addTab("Q/R SCU", scu);
			tabbedPane.addTab("Storage SCP", scp);
			tabbedPane.addTab("Directory", source);
			tabbedPane.addTab("Viewer", viewer);
			tabbedPane.addTab("Elements", editor);
			tabbedPane.addTab("Filter", filter);
			tabbedPane.addTab("Script", script);
			tabbedPane.addTab("Index", index);
			tabbedPane.addTab("Log", logPanel);
			tabbedPane.addTab("Help", help);
			tabbedPane.setSelectedIndex(2);
			tabbedPane.addChangeListener(viewer);
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
