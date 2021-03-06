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
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.border.*;
import org.dcm4che.util.DcmURL;
import org.apache.log4j.*;
import org.rsna.ctp.pipeline.Status;
import org.rsna.ctp.stdstages.dicom.DicomStorageSCU;
import org.rsna.server.HttpResponse;
import org.rsna.ui.ColorPane;
import org.rsna.ui.RowLayout;
import org.rsna.util.DigestUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.IPUtil;
import org.rsna.util.StringUtil;

public class ExportPanel extends BasePanel implements ActionListener {

	static final Logger logger = Logger.getLogger(ExportPanel.class);

	static final int oneSecond = 1000;
	final int connectionTimeout = 20 * oneSecond;
	final int readTimeout = 120 * oneSecond;

	Configuration config;
	JScrollPane jsp;
	JPanel centerPanel;
	JPanel header;
	Footer footer;
	
	DicomHeaderPanel dicomHeaderPanel;
	HttpHeaderPanel httpHeaderPanel;
	
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
	
		//Header
		dicomHeaderPanel = new DicomHeaderPanel();
		httpHeaderPanel = new HttpHeaderPanel();
		if (config.getProps().getProperty("exportProtocol","").equals("dicom"))
			header = dicomHeaderPanel;
		else
			header = httpHeaderPanel;
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
		footer = new Footer(this);
		add(footer, BorderLayout.SOUTH);
	}
	
	class Footer extends JPanel {
		JButton switchProtocols;
		JButton export;
		JButton refresh;
		JButton clear;
		JButton select;
		public Footer(ExportPanel parent) {
			super();
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			setBackground(config.background);
			String protocol = config.getProps().getProperty("exportProtocol");
			if (protocol.equals("dicom")) switchProtocols = new JButton("Switch to HTTP");
			else switchProtocols = new JButton("Switch to DICOM");
			export = new JButton("Export");
			refresh = new JButton("Refresh");
			clear = new JButton("Clear All");
			select = new JButton("Select All");
			add(switchProtocols);
			add(Box.createHorizontalStrut(10));
			add(clear);
			add(Box.createHorizontalStrut(10));
			add(select);
			add(Box.createHorizontalStrut(10));
			add(refresh);
			add(Box.createHorizontalGlue());
			add(export);
			switchProtocols.addActionListener(parent);
			export.addActionListener(parent);
			refresh.addActionListener(parent);
			clear.addActionListener(parent);
			select.addActionListener(parent);
		}
	}
	
	class DicomHeaderPanel extends JPanel implements ActionListener, KeyListener {
		PanelField scpIP;
		PanelField scpPort;
		PanelField scpAET;
		PanelField scuAET;
		JCheckBox enableExport;
		
		public DicomHeaderPanel() {
			super();
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			setBackground(config.background);

			String scpIPString = config.getProps().getProperty("exportSCPIP","");
			String scpPortString = config.getProps().getProperty("exportSCPPort","104");
			String scpAETString = config.getProps().getProperty("exportCalledAET","");
			String scuAETString = config.getProps().getProperty("exportCallingAET","ANONEXPORT");
			enableExport = new JCheckBox("Enable export");
			enableExport.setBackground(config.background);
			enableExport.addActionListener(this);
			enableExport.setSelected(config.getProps().getProperty("enableExport", "yes").equals("yes"));
			scpIP = new PanelField(scpIPString, 150);
			scpIP.addKeyListener(this);
			scpPort = new PanelField(scpPortString);
			scpPort.addKeyListener(this);
			scpAET = new PanelField(scpAETString, 80);
			scpAET.addKeyListener(this);
			scuAET = new PanelField(scuAETString, 80);
			scuAET.addKeyListener(this);

			add(new JLabel(" DICOM Storage SCP:  "));
			add(scpIP);
			add(new JLabel(" : "));
			add(scpPort);
			add(new JLabel("   Called AET:  "));
			add(scpAET);
			add(Box.createHorizontalStrut(10));
			add(new JLabel("Calling AET: "));
			add(scuAET);
			add(Box.createHorizontalGlue());
			add(enableExport);
			add(Box.createHorizontalStrut(10));
		}
		
		public DcmURL getURL() throws Exception {
			return new DcmURL(
				"dicom",
				scpAET.getText().trim(),
				scuAET.getText().trim(),
				scpIP.getText().trim(),
				StringUtil.getInt(scpPort.getText().trim(), 104)
			);
		}
		
		public void keyTyped(KeyEvent event) { }
		public void keyPressed(KeyEvent event) { }
		public void keyReleased(KeyEvent event) {
			config.getProps().setProperty("exportSCPIP", scpIP.getText().trim());
			config.getProps().setProperty("exportSCPPort", scpPort.getText().trim());
			config.getProps().setProperty("exportCalledAET", scpAET.getText().trim());
			config.getProps().setProperty("exportCallingAET", scuAET.getText().trim());
		}
		public void actionPerformed(ActionEvent event) {
			boolean enb = enableExport.isSelected();
			config.getProps().setProperty("enableExport", (enb?"yes":"no"));
		}
	}
	
	class HttpHeaderPanel extends JPanel implements ActionListener, KeyListener {
		PanelField httpURLField;
		JCheckBox enableExport;
		
		public HttpHeaderPanel() {
			super();
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			setBackground(config.background);

			String httpURLString = config.getProps().getProperty("exportHttpURL","127.0.0.1:9555");
			enableExport = new JCheckBox("Enable export");
			enableExport.setBackground(config.background);
			enableExport.addActionListener(this);
			enableExport.setSelected(config.getProps().getProperty("enableExport", "yes").equals("yes"));
			httpURLField = new PanelField(httpURLString, 300);
			httpURLField.addKeyListener(this);

			add(new JLabel(" HTTP Storage URL:  "));
			add(httpURLField);
			add(Box.createHorizontalGlue());
			add(enableExport);
			add(Box.createHorizontalStrut(10));
		}
		
		public String getURL() throws Exception {
			return "http://" + httpURLField.getText().trim() + "/papi/v1/import/file";
		}
		
		public String getEventIDRequestURL() throws Exception {
			return "http://" + httpURLField.getText().trim() + "/papi/v1/import/event?source="
				+ Configuration.getInstance().getProps().getProperty("SITEID");
		}
		
		public void keyTyped(KeyEvent event) { }
		public void keyPressed(KeyEvent event) { }
		public void keyReleased(KeyEvent event) {
			config.getProps().setProperty("exportHttpURL", httpURLField.getText().trim());
		}
		public void actionPerformed(ActionEvent event) {
			boolean enb = enableExport.isSelected();
			config.getProps().setProperty("enableExport", (enb?"yes":"no"));
		}
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
		if (source.equals(footer.export)) {
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
		else if (source.equals(footer.clear)) {
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					((CaseCheckBox)c).setSelected(false);
				}
			}
		}
		else if (source.equals(footer.select)) {
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					CaseCheckBox ccb = (CaseCheckBox)c;
					File exportFile = new File(ccb.file, hiddenExportFilename);
					ccb.setSelected(!exportFile.exists());
				}
			}
		}
		else if (source.equals(footer.refresh)) {
			listCases();
			centerPanel.revalidate();
			centerPanel.repaint();
		}
		else if (source.equals(footer.switchProtocols)) {
			if (header instanceof DicomHeaderPanel) {
				remove(dicomHeaderPanel);
				header = httpHeaderPanel;
				footer.switchProtocols.setText("Switch to DICOM");
				config.getProps().setProperty("exportProtocol","http");
			}
			else {
				remove(httpHeaderPanel);
				header = dicomHeaderPanel;
				footer.switchProtocols.setText("Switch to HTTP");
				config.getProps().setProperty("exportProtocol","dicom");
			}
			add(header, BorderLayout.NORTH);
			repaint();
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
			if (header instanceof DicomHeaderPanel) {
				DcmURL url = dicomHeaderPanel.getURL();
				for (File caseDir : cases) {
					exportExecutor.execute(
						new DicomExportThread(caseDir, url, dicomHeaderPanel.enableExport.isSelected())
					);
				}
			}
			else {
				String url = httpHeaderPanel.getURL();
				String idRequestURL = httpHeaderPanel.getEventIDRequestURL();
				for (File caseDir : cases) {
					exportExecutor.execute(
						new HttpExportThread(caseDir, url, idRequestURL, httpHeaderPanel.enableExport.isSelected())
					);
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Export failed", ex);
		}
	}
	
	class DicomExportThread extends Thread {
		File dir;
		DcmURL url;
		boolean enableExport;
		File expFile;
		DicomStorageSCU scu;
		
		public DicomExportThread(File dir, DcmURL url, boolean enableExport) {
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
				else logger.warn("Export: DICOM transmission failed: " + e.getMessage());
			}
			return false;
		}
	}
	
	class HttpExportThread extends Thread {
		File dir;
		String url;
		String idRequestURL;
		boolean enableExport;
		File expFile;
		String eventID = "0";
		
		public HttpExportThread(File dir, String url, String idRequestURL, boolean enableExport) {
			super();
			this.dir = dir;
			this.url = url;
			this.idRequestURL = idRequestURL;
			this.enableExport = enableExport;
			this.expFile = new File(dir, hiddenExportFilename);
		}
		
		public void run() {
			eventID = getImportEventID();
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
						result = exportFile(file, eventID);
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
		
		//curl -X PUT http://localhost/papi/v1/import/event?source=some+useful+message
		//{"status":"success","import_event_id":15}
		private String getImportEventID() {
			HttpURLConnection conn = null;
			try {
				URL u = new URL(idRequestURL);
				conn = HttpUtil.getConnection(u);
				conn.setReadTimeout(connectionTimeout);
				conn.setConnectTimeout(readTimeout);
				conn.setRequestMethod("PUT");
				conn.connect();
				int responseCode = conn.getResponseCode();
				logger.debug("getImportEventID responseCode: " + responseCode);
				logger.debug("...url: " + u.toString());
				String text = FileUtil.getTextOrException( conn.getInputStream(), FileUtil.utf8, false );
				conn.disconnect();
				logger.debug("...got import_event_id: "+text);
				if (text.contains("\"status\":\"success\"") && text.contains("\"import_event_id\":")) {
					text = text.replaceAll("[^0-9]", "");
				}
				else text = "0";
				logger.debug("...returning "+text);
				return text;
			}
			catch (Exception unable) { 
				logger.debug("Unable to get import_event_id; returning 0");
				return "0"; 
			}
		}
		
		//Example URL for export to POSDA:
		// http://f20de65151a2.ngrok.io/papi/v1/import/file?import_event_id=...&digest=ac6aec03c70f9c3063e5bb6cc190d34e
		private boolean exportFile(File file, String eventID) {
			HttpURLConnection conn = null;
			OutputStream svros = null;
			try {
				String hash = getDigest(file).toLowerCase();
				URL u = new URL(url + "?import_event_id="+eventID+"&digest="+hash);
				//logger.debug(u.toString());
				boolean result = true;

				conn = HttpUtil.getConnection(u);
				conn.setReadTimeout(connectionTimeout);
				conn.setConnectTimeout(readTimeout);
				conn.setRequestMethod("PUT");
				conn.connect();
				svros = conn.getOutputStream();
				FileUtil.streamFile(file, svros);
				int responseCode = conn.getResponseCode();
				
				//Get the response text
				String responseText = "";
				try { responseText = FileUtil.getTextOrException( conn.getInputStream(), FileUtil.utf8, false ); }
				catch (Exception ex) { logger.warn("Unable to read response: "+ex.getMessage()); }
				conn.disconnect();
				if (logger.isDebugEnabled()) {
					logger.info("Server response "+responseCode+" for: " + file);
					logger.warn("Response text: "+responseText);
				}
				else {
					if (responseCode == HttpResponse.unprocessable) {
						logger.warn("Unprocessable response from server for: " + file);
						logger.warn("Response text: "+responseText);
						result = false;
					}
					else if (responseCode != HttpResponse.ok) {
						logger.warn("Failure response from server ("+responseCode+") for: " + file);
						logger.warn("Response text: "+responseText);
						result = false;
					}
				}
				conn.disconnect();
				return result;
				
			}
			catch (Exception e) {
				if (logger.isDebugEnabled()) logger.debug("Export: transmission failed: " + e.getMessage(), e);
				else logger.warn("Export: HTTP transmission failed: " + e.getMessage());
			}
			return false;
		}
		
		private String getDigest(File file) {
			String result = "";
			BufferedInputStream bis = null;
			DigestInputStream dis = null;
			try {
				MessageDigest md = MessageDigest.getInstance("MD5");
				md.reset();
				bis = new BufferedInputStream( new FileInputStream( file ) );
				dis = new DigestInputStream( bis, md );
				while (dis.read() != -1) ; //empty loop
				result = bytesToHex(md.digest());
			}
			catch (Exception ex) { result = ""; }
			finally {
				try { dis.close(); }
				catch (Exception ignore) { }
				try { bis.close(); }
				catch (Exception ignore) { }
			}
			return result.toString();
		}
	
		private String bytesToHex(byte[] bytes) {
			StringBuilder sb = new StringBuilder();
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
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
