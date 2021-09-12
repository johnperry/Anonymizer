/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.border.*;
import org.dcm4che.util.DcmURL;
import org.apache.log4j.*;
import org.dcm4che.dict.Tags;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.dicom.SimpleDicomStorageSCP;
import org.rsna.ui.ColorPane;
import org.rsna.ui.RowLayout;
import org.rsna.util.FileUtil;
import org.rsna.util.IPUtil;
import org.rsna.util.StringUtil;
import org.dcm4che.data.Dataset;
import org.dcm4che.net.Dimse;
import org.dcm4che.dict.Tags;


public class SCUPanel extends BasePanel implements ActionListener, KeyListener {

	static final Logger logger = Logger.getLogger(SCUPanel.class);
	public static ColorPane cp;
	
	boolean queryMode = true;

	Configuration config;
	File scpDirectory;
	boolean scpRunning = false;
	JScrollPane resultsScrollPane;
	ResultsPanel resultsPanel;
	JButton clear;
	JButton selectAll;
	JButton deselectAll;
	JButton switchModes;
	JButton openFile;
	JButton query;
	JButton retrieve;
	PanelField scpIP;
	PanelField scpPort;
	PanelField scpAET;
	PanelField scuAET;
	BasePanel queryModePanel = null;
	BasePanel accessionModePanel = null;
	ColorPane ampCP = null;
	boolean forceIVRLE = false;
	boolean renameToSOPIUID = false;
	QueryPanel queryPanel;
	DicomQRSCU dicomQRSCU = null;
	JFileChooser chooser = null;
	CGetCMovePanel getmove = null;
	
	ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
	ExecutorService retrieveExecutor = Executors.newSingleThreadExecutor();

	Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );

	static SCUPanel scuPanel = null;

	public static synchronized SCUPanel getInstance() {
		if (scuPanel == null) scuPanel = new SCUPanel();
		return scuPanel;
	}

	protected SCUPanel() {
		super();
		config = Configuration.getInstance();
	
		//UI Components
		cp = new ColorPane();
		cp.setScrollableTracksViewportWidth(false);
		clear = new JButton("Clear Query Fields");
		clear.addActionListener(this);
		selectAll = new JButton("Select All");
		selectAll.addActionListener(this);
		deselectAll = new JButton("Deselect All");
		deselectAll.addActionListener(this);
		switchModes = new JButton("Switch to Accession Mode");
		switchModes.addActionListener(this);
		query = new JButton("Query");
		query.addActionListener(this);
		retrieve = new JButton("Import");
		retrieve.addActionListener(this);
		//retrieve.setEnabled(false);
		openFile = new JButton("Open Accession List File");
		openFile.addActionListener(this);
		openFile.setVisible(false);
		String scpIPString = config.getProps().getProperty("qrscpIP","");
		String scpPortString = config.getProps().getProperty("qrscpPort","106");
		String scpAETString = config.getProps().getProperty("qrscpAET","");
		String scuAETString = config.getProps().getProperty("qrscuAET","ANONQR");
		getmove = new CGetCMovePanel();
		scpIP = new PanelField(scpIPString, 150);
		scpIP.addKeyListener(this);
		scpPort = new PanelField(scpPortString);
		scpPort.addKeyListener(this);
		scpAET = new PanelField(scpAETString, 70);
		scpAET.addKeyListener(this);
		scuAET = new PanelField(scuAETString, 70);
		scuAET.addKeyListener(this);
		
		//Header
		Box header = Box.createHorizontalBox();
		header.setBackground(config.background);
		header.add(new JLabel(" DICOM Q/R SCP:  "));
		header.add(scpIP);
		header.add(new JLabel(" : "));
		header.add(scpPort);
		header.add(new JLabel("   AET:  "));
		header.add(scpAET);
		header.add(Box.createHorizontalStrut(10));
		//header.add(getmove);
		header.add(Box.createHorizontalGlue());
		header.add(new JLabel("DICOM Q/R SCU AET: "));
		header.add(scuAET);
		header.add(Box.createHorizontalStrut(10));
		add(header, BorderLayout.NORTH);

		//Main panel
		//Start with the Query Mode Panel
		queryModePanel = new BasePanel();
		Border inner = BorderFactory.createEmptyBorder(2, 0, 0, 0);
		Border outer = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		queryModePanel.setBorder(BorderFactory.createCompoundBorder(outer, inner));
		//Query Parameters
		queryPanel = new QueryPanel();
		queryPanel.patientName.addActionListener(this);
		queryPanel.patientID.addActionListener(this);
		queryPanel.studyDate.addActionListener(this);
		queryPanel.accession.addActionListener(this);
		queryPanel.modality.addActionListener(this);
		ParamsPanel pp = new ParamsPanel("Query Parameters", 10, 10, queryPanel);
		queryModePanel.add(pp, BorderLayout.NORTH);
		//Query Results
		resultsPanel = new ResultsPanel("Query Results", 10, 10);
		resultsScrollPane = new JScrollPane();
		resultsScrollPane.getVerticalScrollBar().setUnitIncrement(10);
		resultsScrollPane.setViewportView(resultsPanel);
		resultsScrollPane.getViewport().setBackground(Color.white);
		queryModePanel.add(resultsScrollPane, BorderLayout.CENTER);
		add(queryModePanel, BorderLayout.CENTER);
		
		//Now make the Accession Mode Panel, but don't load it
		accessionModePanel = new BasePanel();
		Border ampInner = BorderFactory.createEmptyBorder(2, 0, 0, 0);
		Border ampOuter = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		accessionModePanel.setBorder(BorderFactory.createCompoundBorder(ampOuter, ampInner));
		//Title panel
		Box ampHeader = Box.createVerticalBox(); //new JPanel(new FlowLayout(FlowLayout.CENTER));
		ampHeader.setBackground(config.background);
		JLabel ampTitle = new JLabel("List Accession Numbers for Retrieval");		
		ampTitle.setFont( new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 ) );
		ampTitle.setForeground(Color.BLUE);
		ampTitle.setAlignmentX(0.5f);
		ampHeader.add(Box.createVerticalStrut(15));
		ampHeader.add(ampTitle);
		ampHeader.add(Box.createVerticalStrut(15));
		accessionModePanel.add(ampHeader, BorderLayout.NORTH);
		//Accession Number list panel
		JPanel ampListPanel = new JPanel();
		ampCP = new ColorPane();
		ampCP.setText("// Enter a list of Accession Numbers, one per line.\n\n");
		JScrollPane ampScrollPane = new JScrollPane();
		ampScrollPane.getVerticalScrollBar().setUnitIncrement(10);
		ampScrollPane.setViewportView(ampCP);
		ampScrollPane.getViewport().setBackground(Color.white);
		accessionModePanel.add(ampScrollPane, BorderLayout.CENTER);
		//add(accessionModePanel, BorderLayout.CENTER);
		
		//Footer
		Box footer = Box.createHorizontalBox();
		footer.add(switchModes);
		footer.add(Box.createHorizontalStrut(10));
		footer.setBackground(config.background);
		footer.add(clear);
		footer.add(Box.createHorizontalStrut(10));
		footer.add(selectAll);
		footer.add(Box.createHorizontalStrut(10));
		footer.add(deselectAll);
		footer.add(Box.createHorizontalGlue());
		footer.add(openFile);
		footer.add(query);
		footer.add(Box.createHorizontalStrut(10));
		footer.add(retrieve);
		add(footer, BorderLayout.SOUTH);
	}
	
	public void switchModes() {
		if (queryMode) {
			switchModes.setText("Switch to Query Mode");
			remove(queryModePanel);
			add(accessionModePanel, BorderLayout.CENTER);
			clear.setVisible(false);
			selectAll.setVisible(false);
			deselectAll.setVisible(false);
			query.setVisible(false);
			openFile.setVisible(true);
			ampCP.requestFocus();
		}
		else {
			switchModes.setText("Switch to Accession Mode");
			remove(accessionModePanel);
			add(queryModePanel, BorderLayout.CENTER);
			clear.setVisible(true);
			selectAll.setVisible(true);
			deselectAll.setVisible(true);
			query.setVisible(true);
			openFile.setVisible(false);
		}
		queryMode = !queryMode;
		updateUI();
	}
	
	public void setFocus() {
		if (queryMode) queryPanel.patientName.requestFocus();
		else ampCP.requestFocus();
	}
	
	class CGetCMovePanel extends JPanel implements ActionListener {
		JRadioButton cget;
		JRadioButton cmove;
		public CGetCMovePanel() {
			super();
			setBackground(config.background);
			boolean cmoveSel = true; //was config.getProps().getProperty("cmove", "yes").equals("yes");
			cget = new JRadioButton("C-GET");
			cget.setBackground(config.background);
			cget.setSelected(!cmoveSel);
			cget.addActionListener(this);
			cmove = new JRadioButton("C-MOVE");
			cmove.setBackground(config.background);
			cmove.setSelected(cmoveSel);
			cmove.addActionListener(this);
			ButtonGroup bg = new ButtonGroup();
			bg.add(cget);
			bg.add(cmove);
			add(cget);
			add(cmove);
		}
		public void actionPerformed(ActionEvent e) {
			config.getProps().setProperty("cmove", (cget.isSelected() ? "no" : "yes"));
		}
		public boolean isCGet() {
			return cget.isSelected();
		}
		public boolean isCMove() {
			return cmove.isSelected();
		}
	}
	
	class ParamsPanel extends JPanel {
		public ParamsPanel(String title, int marginTop, int marginBottom, QueryPanel queryPanel) {
			super();
			setBackground(config.background);
			Box box = Box.createVerticalBox();
			JLabel panelTitle = new JLabel(title);
			panelTitle.setFont( new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 ) );
			panelTitle.setForeground(Color.BLUE);
			panelTitle.setAlignmentX(0.5f);
			box.add(Box.createVerticalStrut(marginTop));
			box.add(panelTitle);
			box.add(Box.createVerticalStrut(marginBottom));
			box.add(queryPanel);
			this.add(box);
		}		
	}
	class QueryPanel extends JPanel {
		public PanelField patientName = new PanelField("", 250);
		public PanelField patientID = new PanelField("", 250);
		public PanelField studyDate = new PanelField("", 250);
		public PanelField accession = new PanelField("", 250);
		public PanelField modality = new PanelField("", 250);
		public QueryPanel() {
			super();
			setBackground(config.background);
			add(new QPanel());
		}
		class QPanel extends JPanel {
			public QPanel() {
				super();
				setLayout(new RowLayout());
				setBackground(config.background);
				addRow("Patient Name:", patientName);
				addRow("Patient ID:", patientID);
				addRow("Study Date:", studyDate);
				addRow("Accession:", accession);
				addRow("Modality:", modality);
			}
			private void addRow(String text, PanelField panelField) {
				add(new JLabel(text));
				add(panelField);
				add(RowLayout.crlf());
			}
		}
		public void clear() {
			patientName.setText("");
			patientID.setText("");
			studyDate.setText("");
			modality.setText("");
		}
		public Hashtable<String,String> getParams() {
			Hashtable<String,String> params = new Hashtable<String,String>();
			String s;
			if ( (s=patientName.getText()).length() != 0 ) params.put("PatientName", s);
			if ( (s=patientID.getText()).length() != 0 ) params.put("PatientID", s);
			if ( (s=studyDate.getText()).length() != 0 ) params.put("StudyDate", s);
			//handle optional elements specially 
			params.put("ModalitiesInStudy", modality.getText());
			params.put("AccessionNumber", accession.getText());
			return params;
		}
	}

	class ResultsPanel extends JPanel {
		RPanel rPanel;
		public ResultsPanel(String title, int marginTop, int marginBottom) {
			super();
			setLayout(new BorderLayout());
			setBackground(config.background);
			Box topBox = Box.createVerticalBox();
			JLabel panelTitle = new JLabel(title);
			panelTitle.setFont( new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 ) );
			panelTitle.setForeground(Color.BLUE);
			panelTitle.setAlignmentX(0.5f);
			topBox.add(Box.createVerticalStrut(marginTop));
			topBox.add(panelTitle);
			topBox.add(Box.createVerticalStrut(marginBottom));
			this.add(topBox, BorderLayout.NORTH);
			rPanel = new RPanel();
			JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			bottomPanel.setBackground(config.background);
			bottomPanel.add(rPanel);
			this.add(bottomPanel, BorderLayout.CENTER);
		}
		public void displayResults(List list) {
			rPanel.displayResults(list);
		}
		public LinkedList<Dataset> getSelectedResults() {
			return rPanel.getSelectedResults();
		}
		public void clear() {
			rPanel.clear();
		}
		public void selectAll() {
			rPanel.selectAll();
		}
		public void deselectAll() {
			rPanel.deselectAll();
		}
	}
	class RPanel extends JPanel {
		public RPanel() {
			super();
			setBackground(config.background);
			setLayout(new RowLayout(20,5));
		}
		public void clear() {
			this.removeAll();
			revalidate();
		}
		public void displayResults(List list) {
			clear();
			if (list.size() > 0) {
				addHeadingsRow();
				for (Object obj : list) {
					Dimse dimse = (Dimse)obj;
					try {
						Dataset ds = dimse.getDataset();
						addRow(ds);
					}
					catch (Exception ignore) { }
				}
			}
			else {
				add(new HeadingLabel("No query matches found"));
				add(RowLayout.crlf());
			}
			revalidate();
		}
		public void selectAll() {
			for (Component c : getComponents()) {
				if (c instanceof ResultCheckBox) {
					ResultCheckBox cb = (ResultCheckBox)c;
					cb.setSelected(true);
				}
			}
		}
		public void deselectAll() {
			for (Component c : getComponents()) {
				if (c instanceof ResultCheckBox) {
					ResultCheckBox cb = (ResultCheckBox)c;
					cb.setSelected(false);
				}
			}
		}
		public LinkedList<Dataset> getSelectedResults() {
			LinkedList<Dataset> list = new LinkedList<Dataset>();
			for (Component c : getComponents()) {
				if (c instanceof ResultCheckBox) {
					ResultCheckBox cb = (ResultCheckBox)c;
					if (cb.isSelected()) list.add(cb.ds);
				}
			}
			return list;
		}
		private void addHeadingsRow() {
			add(Box.createHorizontalStrut(5));
			add(new HeadingLabel("PatientName"));
			add(new HeadingLabel("PatientID"));
			add(new HeadingLabel("StudyDate"));
			add(new HeadingLabel("Accession", 0.5f));
			add(new HeadingLabel("Modality", 0.5f));
			add(new HeadingLabel("Series", 0.5f));
			add(new HeadingLabel("Images", 0.5f));
			add(RowLayout.crlf());
		}
		private void addRow(Dataset ds) {
			add(new ResultCheckBox(ds));
			add(new ResultLabel(ds.getString(Tags.PatientName)));
			add(new ResultLabel(ds.getString(Tags.PatientID)));
			add(new ResultLabel(ds.getString(Tags.StudyDate)));
			add(new ResultLabel(ds.getString(Tags.AccessionNumber)));
			add(new ResultLabel(ds.getString(Tags.ModalitiesInStudy), 0.5f));
			add(new ResultLabel(ds.getString(Tags.NumberOfStudyRelatedSeries), 0.5f));
			add(new ResultLabel(ds.getString(Tags.NumberOfStudyRelatedInstances), 0.5f));
			add(RowLayout.crlf());
		}
		class HeadingLabel extends JLabel {
			public HeadingLabel(String text) {
				this(text, 0.0f);
			}
			public HeadingLabel(String text, float alignmentX) {
				super(text);
				setFont( new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 12 ) );
				setForeground(Color.BLUE);
				setAlignmentX(alignmentX);
			}
		}
		class ResultCheckBox extends JCheckBox {
			Dataset ds;
			public ResultCheckBox(Dataset ds) {
				super();
				setBackground(config.background);
				this.ds = ds;
			}
		}
		class ResultLabel extends JTextField {
			public ResultLabel(String text) {
				this(text, 0.0f);
			}
			public ResultLabel(String text, float alignmentX) {
				super(text);
				//setBackground(config.background);
				setBorder(null);
				setOpaque(false);
				setEditable(false);
				setFont( mono );
				setAlignmentX(alignmentX);
			}
		}
	}
	
	private DcmURL getQRURL() {
		return new DcmURL(
			"dicom",
			scpAET.getText().trim(),
			scuAET.getText().trim(),
			scpIP.getText().trim(),
			StringUtil.getInt(scpPort.getText().trim(), 104)
		);
	}

	public void actionPerformed(ActionEvent event) {
		DcmURL qrURL = getQRURL();
		Object source = event.getSource();
		if (source.equals(clear)) {
			queryPanel.clear();
		}
		else if (source.equals(deselectAll)) {
			resultsPanel.deselectAll();
		}
		else if (source.equals(selectAll)) {
			resultsPanel.selectAll();
		}
		else if (source.equals(switchModes)) {
			switchModes();
		}
		else if (source.equals(openFile)) {
			if (chooser == null) {
				if (chooser == null) {
					chooser = new JFileChooser();
					chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					chooser.setSelectedFile(new File("."));
				}
			}
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				File file = chooser.getSelectedFile();
				ampCP.setText(FileUtil.getText(file));
			}
		}
		else if (source.equals(query) || (source instanceof PanelField)) {
			Hashtable<String,String> params = queryPanel.getParams();
			if (params.size() > 0) {
				resultsPanel.clear();
				queryExecutor.execute(new QueryThread(qrURL, params));
			}
			else JOptionPane.showMessageDialog(this, "Enter at least one query field");
		}
		else if (source.equals(retrieve)) {
			String destination = SCPPanel.getInstance().getAET();
			if (queryMode) {
				LinkedList<Dataset> datasets = resultsPanel.getSelectedResults();
				if (datasets.size() > 0) {
					retrieveExecutor.execute(new RetrieveThread(qrURL, datasets, destination));
				}
			}
			else {
				String accNums = ampCP.getText();
				new AccessionThread(qrURL, accNums, destination).start();
			}
		}
	}
	
	class QueryThread extends Thread {
		DcmURL qrURL;
		Hashtable<String,String> params;
		DicomQRSCU dicomQRSCU;
		public QueryThread(DcmURL qrURL, Hashtable<String,String> params) {
			this.qrURL = qrURL;
			this.params = params;
		}
		public void run() {
			try {
				dicomQRSCU = new DicomQRSCU(qrURL);
				if (dicomQRSCU.open()) {
					final List list = dicomQRSCU.doStudyRootQuery(params);
					final ResultsPanel panel = resultsPanel;
					final JButton button = retrieve;
					SwingUtilities.invokeLater(
						new Runnable() {
							public void run() {
								panel.displayResults(list);
								button.setEnabled(true);
							}
						}
					);
				}
				else logger.warn("Unable to connect to Q/R SCP at "+qrURL);
			}
			catch (Exception unable) {
				logger.warn("Query Failed: "+qrURL, unable);
			}
			finally { close(); }
		}
		private void close() {
			try { dicomQRSCU.close(); }
			catch (Exception ignore) { }
		}
	}
			
	class RetrieveThread extends Thread {
		DcmURL qrURL;
		LinkedList<Dataset> datasets;
		String destination;
		DicomQRSCU dicomQRSCU;
		public RetrieveThread(DcmURL qrURL, LinkedList<Dataset> datasets, String destination) {
			this.qrURL = qrURL;
			this.datasets = datasets;
			this.destination = destination;
		}
		public void run() {
			try {
				dicomQRSCU = new DicomQRSCU(qrURL);
				if (dicomQRSCU.open()) {
					for (Dataset ds : datasets) {
						if (dicomQRSCU.doMove(ds, destination) != 0) {
							logger.warn("Retrieve Failed: "+qrURL);
						}
					}
				}
				else logger.warn("Unable to connect to Q/R SCP at "+qrURL);
			}
			catch (Exception unable) {
				logger.warn("Retrieve Failed: "+qrURL, unable);
			}
			finally { close(); }
		}
		private void close() {
			try { dicomQRSCU.close(); }
			catch (Exception ignore) { }
		}
	}
	
	class AccessionThread extends Thread {
		DcmURL qrURL;
		String accNums;
		String destination;
		DicomQRSCU dicomQRSCU;
		SCPPanel scpPanel;
		boolean useCGet;
		int startingSCPImageCount;
		int imageCount;
		int studyCount;
		
		public AccessionThread(DcmURL qrURL, String accNums, String destination) {
			this.qrURL = qrURL;
			this.accNums = accNums;
			this.destination = destination;
			useCGet = getmove.isCGet();
		}
		public void run() {
			scpPanel = SCPPanel.getInstance();
			studyCount = 0;
			imageCount = 0;
			if (!useCGet) doCMove();
			else doCGet();
		}
		
		private void doCMove() {
			List list = null;
			try {
				String[] lines = accNums.split("\n");
				ampCP.setText("");
				for (String line : lines) {
					String an = filter(line);
					if (!an.equals("")) {
						waitForSCPToCatchUp();
						dicomQRSCU = new DicomQRSCU(qrURL);
						if (dicomQRSCU.open()) {
							Hashtable<String,String> params = new Hashtable<String,String>();
							params.put("AccessionNumber", an);
							ampCP.print(String.format("//%s - %s: ", StringUtil.getTime(":"), an));
							list = dicomQRSCU.doStudyRootQuery(params);
							ampCP.print(list.size() + " match" + ((list.size() == 1)?"":"es"));
							int accessionImageCount = 0;
							boolean ok = true;
							long time = System.currentTimeMillis();
							if (list.size() > 0) {
								for (Object obj : list) {
									Dimse dimse = (Dimse)obj;
									try {
										Dataset ds = dimse.getDataset();
										int result;
										int n = StringUtil.getInt(ds.getString(Tags.NumberOfStudyRelatedInstances));
										if ( (result = dicomQRSCU.doMove(ds, destination)) == 0) {;
											studyCount++;
											accessionImageCount += n;
										}
										else {
											String resultString = String.format("%04x", result);
											logger.warn("C-Move request failed ["+resultString+"]: "+qrURL);
											ampCP.print("; ");
											ampCP.print(Color.RED, "[xfr failed - "+resultString+"]");
											ampCP.print(Color.BLACK, "");
											ok = false;
										}
									}
									catch (Exception ex) { logger.warn("Transfer failed", ex); }
								}
								if (ok) ampCP.print("; "+accessionImageCount+" images");
								time = System.currentTimeMillis() - time;
								ampCP.print(String.format(" [%.3f seconds]", ((double)time)/1000.));
								imageCount += accessionImageCount;
							}
							ampCP.println("");
							try { Thread.sleep(100); }
							catch (Exception ignore) { }
							close();
						}
						else {
							ampCP.println("Unable to connect to Q/R SCP at "+qrURL);
							logger.warn("Unable to connect to Q/R SCP at "+qrURL);
						}
					}
				}
				ampCP.println("\n" + studyCount + " stud" + ( (studyCount==1)?"y":"ies" ) 
								+ " and "+imageCount + " image" + ( (imageCount==1)?"":"s" ) + " imported");
			}
			catch (Exception unable) {
				logger.warn("Retrieve Failed: "+qrURL, unable);
			}
			finally { close(); }
		}
		
		private synchronized void waitForSCPToCatchUp() {
			long startTime = System.currentTimeMillis();
			while (scpPanel.getQueueSize() > 100) {
				try { Thread.sleep(10000); }
				catch (Exception ex) { }
				if ((System.currentTimeMillis() - startTime) > 600000) break;
			}
		}

		private void doCGet() {
			ampCP.println("C-GET not implemented");
		}
		private String filter(String s) {
			int k = s.indexOf("//");
			if (k != -1) s = s.substring(0,k);
			return s.trim();
		}
		private void close(String logText) {
			if (logText != null) logger.info(logText);
			close();
		}
		private void close() {
			try { dicomQRSCU.close(); }
			catch (Exception ignore) { }
		}
	}
		
	public void keyTyped(KeyEvent event) { }
	public void keyPressed(KeyEvent event) { }
	public void keyReleased(KeyEvent event) {
		config.getProps().setProperty("qrscpIP", scpIP.getText().trim());
		config.getProps().setProperty("qrscpPort", scpPort.getText().trim());
		config.getProps().setProperty("qrscpAET", scpAET.getText().trim());
		config.getProps().setProperty("qrscuAET", scuAET.getText().trim());
	}
	
	class IPLabel extends JLabel {
		public IPLabel(String text) {
			super(text);
			setFont(mono);
		}
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
		public String getText() {
			return super.getText().trim();
		}
	}
	
	private DicomObject getDicomObject(File file) {
		try { return new DicomObject(file); }
		catch (Exception ex) { return null; }
	}
}
