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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.border.*;
import org.apache.log4j.*;
import org.rsna.ctp.pipeline.Status;
import org.rsna.ctp.stdstages.dicom.DicomStorageSCU;
import org.rsna.ui.ColorPane;
import org.rsna.ui.RowLayout;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.IPUtil;
import org.rsna.util.StringUtil;


public class ExportPanel extends BasePanel implements ActionListener, KeyListener {

	static final Logger logger = Logger.getLogger(ExportPanel.class);

	Configuration config;
	JScrollPane jsp;
	JButton export;
	JButton refresh;
	JButton clear;
	JButton select;
	JCheckBox enableExport;
	JPanel centerPanel;
	
	PanelField scpIP;
	PanelField scpPort;
	PanelField scpAET;
	PanelField scuAET;

	Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );
	Font titleFont = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 );
	Font columnHeadingFont = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 14 );

	static ExportPanel exportPanel = null;
	
	ExecutorService exportExecutor = Executors.newSingleThreadExecutor();
	static final String hiddenExportFilename = "..export";

	public static synchronized ExportPanel getInstance() {
		if (exportPanel == null) exportPanel = new ExportPanel();
		return exportPanel;
	}

	protected ExportPanel() {
		super();
		config = Configuration.getInstance();
	
		//UI Components
		export = new JButton("Export");
		export.addActionListener(this);
		refresh = new JButton("Refresh");
		refresh.addActionListener(this);
		clear = new JButton("Clear All");
		clear.addActionListener(this);
		select = new JButton("Select All");
		select.addActionListener(this);
		
		enableExport = new JCheckBox("Enable export");
		enableExport.setBackground(config.background);
		enableExport.addActionListener(this);
		enableExport.setSelected(config.getProps().getProperty("enableExport", "yes").equals("yes"));
		
		String scpIPString = config.getProps().getProperty("exportSCPIP","");
		String scpPortString = config.getProps().getProperty("exportSCPPort","104");
		String scpAETString = config.getProps().getProperty("exportCalledAET","");
		String scuAETString = config.getProps().getProperty("exportCallingAET","ANONEXPORT");
		scpIP = new PanelField(scpIPString, 150);
		scpIP.addKeyListener(this);
		scpPort = new PanelField(scpPortString);
		scpPort.addKeyListener(this);
		scpAET = new PanelField(scpAETString, 80);
		scpAET.addKeyListener(this);
		scuAET = new PanelField(scuAETString, 80);
		scuAET.addKeyListener(this);

		//Header
		Box header = Box.createHorizontalBox();
		header.setBackground(config.background);
		header.add(new JLabel(" DICOM Storage SCP:  "));
		header.add(scpIP);
		header.add(new JLabel(" : "));
		header.add(scpPort);
		header.add(new JLabel("   Called AET:  "));
		header.add(scpAET);
		header.add(Box.createHorizontalStrut(10));
		header.add(new JLabel("Calling AET: "));
		header.add(scuAET);
		header.add(Box.createHorizontalGlue());
		header.add(enableExport);
		header.add(Box.createHorizontalStrut(10));
		add(header, BorderLayout.NORTH);

		//Main panel
		BasePanel mainPanel = new BasePanel();
		Border inner = BorderFactory.createEmptyBorder(2, 0, 0, 0);
		Border outer = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		mainPanel.setBorder(BorderFactory.createCompoundBorder(outer, inner));
		//Put in the title
		JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 10));
		titlePanel.setBackground(config.background);
		JLabel panelTitle = new JLabel("Export Studies");
		panelTitle.setFont(titleFont);
		panelTitle.setForeground(Color.BLUE);
		titlePanel.add(panelTitle);
		mainPanel.add(titlePanel, BorderLayout.NORTH);
		//Put a scroll pane in the center panel
		JScrollPane jsp = new JScrollPane();
		jsp.setBackground(config.background);
		jsp.getViewport().setBackground(config.background);
		mainPanel.add(jsp, BorderLayout.CENTER);
		//Make a panel to hold the table datasets
		JPanel cp = new JPanel(new FlowLayout(FlowLayout.CENTER));
		cp.setBackground(config.background);
		centerPanel = new BasePanel();
		centerPanel.setLayout(new RowLayout(20, 5));
		cp.add(centerPanel);
		jsp.setViewportView(cp);
		//Now put the main panel in the center of the parent layout
		add(mainPanel, BorderLayout.CENTER);
		
		listCases();
		
		//Footer
		Box footer = Box.createHorizontalBox();
		footer.setBackground(config.background);
		footer.add(clear);
		footer.add(Box.createHorizontalStrut(10));
		footer.add(select);
		footer.add(Box.createHorizontalStrut(10));
		footer.add(refresh);
		footer.add(Box.createHorizontalGlue());
		footer.add(export);
		add(footer, BorderLayout.SOUTH);
	}
	
	private void listCases() {
		centerPanel.removeAll();
		//Put in a vertical margin
		centerPanel.add(Box.createVerticalStrut(10));
		centerPanel.add(RowLayout.crlf());
		//Put in the column headings
		centerPanel.add(Box.createHorizontalStrut(5)); //no heading for the checkboxes
		centerPanel.add(new HeadingLabel("PatientID"));
		centerPanel.add(new HeadingLabel("ExportDate"));
		centerPanel.add(RowLayout.crlf());
		//Put in the cases
		File[] cases = config.getStorageDir().listFiles();
		for (int i=cases.length-1; i>=0; i--) {
			File caseDir = cases[i];
			if (caseDir.isDirectory()) {
				String exportDate = "";
				File export = new File(caseDir, hiddenExportFilename);
				if (export.exists()) {
					exportDate = StringUtil.getDate(export.lastModified(), ".");
				}
				CaseLabel cl = new CaseLabel(exportDate);
				CaseCheckBox cb = new CaseCheckBox(caseDir, cl);
				centerPanel.add(cb);
				centerPanel.add(new CaseLabel(caseDir.getName()));
				centerPanel.add(cl);
				centerPanel.add(RowLayout.crlf());
			}
		}
	}
	
	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(export)) {
			LinkedList<File> cases = new LinkedList<File>();
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					CaseCheckBox ccb = (CaseCheckBox)c;
					if (ccb.isSelected()) cases.add(ccb.file);
				}
			}
			startExport(cases);
		}
		else if (source.equals(clear)) {
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					((CaseCheckBox)c).setSelected(false);
				}
			}
		}
		else if (source.equals(select)) {
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					CaseCheckBox ccb = (CaseCheckBox)c;
					File exportFile = new File(ccb.file, hiddenExportFilename);
					ccb.setSelected(!exportFile.exists());
				}
			}
		}
		else if (source.equals(refresh)) {
			listCases();
			centerPanel.revalidate();
			centerPanel.repaint();
		}
		else if (source.equals(enableExport)) {
			boolean enb = enableExport.isSelected();
			config.getProps().setProperty("enableExport", (enb?"yes":"no"));
		}
	}
	
	public void keyTyped(KeyEvent event) { }
	public void keyPressed(KeyEvent event) { }
	public void keyReleased(KeyEvent event) {
		config.getProps().setProperty("exportSCPIP", scpIP.getText().trim());
		config.getProps().setProperty("exportSCPPort", scpPort.getText().trim());
		config.getProps().setProperty("exportCalledAET", scpAET.getText().trim());
		config.getProps().setProperty("exportCallingAET", scuAET.getText().trim());
	}
	
	class PanelField extends JTextField {
		public PanelField(String text) {
			this(text, 40);
		}
		public PanelField(String text, int width) {
			super(text);
			setFont(mono);
			Dimension d = getPreferredSize();
			d.width = width;
			setMaximumSize(d);
			setMinimumSize(d);
			setPreferredSize(d);
		}
	}
	
	class HeadingLabel extends JLabel {
		public HeadingLabel(String text) {
			this(text, 0.0f);
		}
		public HeadingLabel(String text, float alignmentX) {
			super(text);
			setFont(columnHeadingFont);
			setForeground(Color.BLUE);
			setAlignmentX(alignmentX);
		}
	}
	
	class CaseCheckBox extends JCheckBox {
		File file;
		CaseLabel label;
		public CaseCheckBox(File file, CaseLabel label) {
			super();
			setBackground(config.background);
			this.file = file;
			this.label = label;
		}
	}
	
	class CaseLabel extends JLabel {
		public CaseLabel(String text) {
			this(text, 0.0f);
		}
		public CaseLabel(String text, float alignmentX) {
			super(text);
			setFont( mono );
			setAlignmentX(alignmentX);
		}
	}

	private void startExport(LinkedList<File> cases) {
		try {
			String url = "dicom://"+scpAET.getText().trim()+":"+scuAET.getText().trim()+"@"+
							scpIP.getText().trim() + ":" + scpPort.getText().trim();
			System.out.println(url);
			for (File caseDir : cases) {
				exportExecutor.execute(new ExportThread(caseDir, url, enableExport.isSelected()));
			}
		}
		catch (Exception ex) {
			logger.warn("Export failed", ex);
		}
	}
	
	class ExportThread extends Thread {
		File dir;
		String url;
		boolean enableExport;
		File expFile;
		DicomStorageSCU scu;
		
		public ExportThread(File dir, String url, boolean enableExport) {
			super();
			this.dir = dir;
			this.url = url;
			this.enableExport = enableExport;
			this.expFile = new File(dir, hiddenExportFilename);
			this.scu = new DicomStorageSCU(url, 5000, false, 0, 0, 0, 0);
		}
		
		public void run() {
			if (exportFiles(dir)) {
				FileUtil.setText(expFile, "");
				updateTable(dir);
			}
		}
		
		private boolean exportFiles(File dir) {
			return exportFiles(dir, true);
		}
		
		private boolean exportFiles(File dir, boolean ok) {
			boolean result;
			File[] files = dir.listFiles();
			for (File file : files) {
				if (file.isFile()) {
					//Do not export the expFile or zero-length files
					long fileLength = file.length();
					if (!file.equals(expFile) && (fileLength != 0)) {
						result = exportFile(file);
						ok = ok && result;
					}
				}
				else if (file.isDirectory()) {
					result = exportFiles(file, ok);
					ok = ok && result;
				}
			}
			return ok;
		}
		
		private boolean exportFile(File file) {
			try { if (scu.send(file).equals(Status.OK)) return true; }
			catch (Exception e) {
				if (logger.isDebugEnabled()) logger.debug("Export: transmission failed: " + e.getMessage(), e);
				else logger.warn("Export: transmission failed: " + e.getMessage());
			}
			return false;
		}
	
		//TODO: figure out how to update the centerPanel without
		//destroying any work the user has done while the
		//background threads have been running.
		private void updateTable(File dir) {
			final File caseDir = dir;
			final JPanel panel = centerPanel;
			Runnable r = new Runnable() {
				public void run() {
					Component[] comps = centerPanel.getComponents();
					for (Component c : comps) {
						if (c instanceof CaseCheckBox) {
							CaseCheckBox cb = (CaseCheckBox)c;
							if (cb.file.equals(caseDir)) {
								File expFile = new File(caseDir, hiddenExportFilename);
								if (expFile.exists()) {
									String exportDate = StringUtil.getDate(expFile.lastModified(), ".");
									cb.label.setText(exportDate);
									cb.setSelected(false);
									return;
								}
							}
						}
					}
				}
			};
			SwingUtilities.invokeLater(r);
		}
	}
	
}
