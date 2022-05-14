/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.util.FileUtil;
import org.rsna.ui.ColorPane;
import org.rsna.ui.RowLayout;

import org.apache.log4j.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;  

//imports for index testing
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import javax.swing.SwingUtilities;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

/**
 * A JPanel that provides a user interface for searching the Index.
 */
public class IndexPanel extends JPanel implements ActionListener {

	static final Logger logger = Logger.getLogger(IndexPanel.class);

	private SearchPanel searchPanel;
	private ListPanel listPanel;
	private CheckPanel checkPanel;
	private RebuildPanel rebuildPanel;
	private FooterPanel footerPanel;
	Color background;
	JPanel currentPanel = null;

	/**
	 * Class constructor.
	 */
    public IndexPanel() {
		super();
		Configuration config = Configuration.getInstance();
		setLayout(new BorderLayout());
		background = config.background;
		setBackground(background);

		searchPanel = new SearchPanel();
		listPanel = new ListPanel();
		checkPanel = new CheckPanel();
		rebuildPanel = new RebuildPanel();
		footerPanel = new FooterPanel();
		footerPanel.search.addActionListener(this);
		footerPanel.list.addActionListener(this);
		footerPanel.save.addActionListener(this);
		footerPanel.check.addActionListener(this);
		footerPanel.rebuild.addActionListener(this);
		searchPanel.searchField.addActionListener(this);
		this.add(searchPanel, BorderLayout.CENTER);
		this.add(footerPanel, BorderLayout.SOUTH);
		currentPanel = searchPanel;
	}
	
	public void setFocus() {
		searchPanel.searchField.requestFocus();
	}		

	/**
	 * Implementation of the ActionListener for the Save Changes button.
	 * @param event the event.
	 */
    public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(footerPanel.search) || source.equals(searchPanel.searchField)) {
			if (!currentPanel.equals(searchPanel)) {
				this.remove(currentPanel);
				this.add(searchPanel, BorderLayout.CENTER);
				currentPanel = searchPanel;
				footerPanel.save.setEnabled(false);
				revalidate();
			}
			searchPanel.search();
		}
		else if (source.equals(footerPanel.list)) {
			if (!currentPanel.equals(listPanel)) {
				this.remove(currentPanel);
				this.add(listPanel, BorderLayout.CENTER);
				currentPanel = listPanel;
				footerPanel.save.setEnabled(true);
				revalidate();
			}
			listPanel.list();
		}
		else if (source.equals(footerPanel.save)) {
			listPanel.save();
		}
		else if (source.equals(footerPanel.rebuild)) {
			if (!currentPanel.equals(rebuildPanel)) {
				this.remove(currentPanel);
				this.add(rebuildPanel, BorderLayout.CENTER);
				currentPanel = rebuildPanel;
				footerPanel.save.setEnabled(false);
				revalidate();
			}
			rebuildPanel.rebuildUIDIndex();
		}
		else if (source.equals(footerPanel.check)) {
			if (!currentPanel.equals(checkPanel)) {
				this.remove(currentPanel);
				this.add(checkPanel, BorderLayout.CENTER);
				currentPanel = checkPanel;
				footerPanel.save.setEnabled(false);
				revalidate();
			}
			checkPanel.checkIndexes();
		}
	}
	
	class HeaderPanel extends Panel {
		public HeaderPanel(String title, int marginTop, int marginBottom) {
			super();
			setBackground(background);
			Box box = Box.createVerticalBox();
			JLabel panelTitle = new JLabel(title);
			panelTitle.setFont( new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 ) );
			panelTitle.setForeground(Color.BLUE);
			box.add(Box.createVerticalStrut(marginTop));
			box.add(panelTitle);
			box.add(Box.createVerticalStrut(marginBottom));
			this.add(box);
		}		
	}
	
	class SearchPanel extends JPanel {
		public JTextField searchField = new JTextField("", 20);
		JLabel searchLabel = new JLabel("Anonymized PatientID: ");
		JLabel nameLabel = new JLabel("Original PatientName: ");
		JLabel idLabel = new JLabel("Original PatientID: ");
		JLabel nameResult = new JLabel("???");
		JLabel idResult = new JLabel("???");
		JPanel centerPanel;
		public SearchPanel() {
			super();
			setBackground(background);
			setLayout(new BorderLayout());
			
			add(new HeaderPanel("Search the Patient Index", 50, 30), BorderLayout.NORTH);
			java.awt.Font sans = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 16 );
			java.awt.Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 16 );
			searchField.setFont( mono );
			searchField.setAlignmentX(0f);
			searchLabel.setFont( sans );
			nameLabel.setFont( sans );
			idLabel.setFont( sans );
			nameResult.setFont( mono );
			idResult.setFont( mono );

			centerPanel = new JPanel();
			centerPanel.setLayout(new RowLayout());
			centerPanel.setBackground(background);
			
			centerPanel.add(searchLabel);
			centerPanel.add(searchField);
			centerPanel.add(RowLayout.crlf());

			centerPanel.add(Box.createVerticalStrut(15));
			centerPanel.add(RowLayout.crlf());
			
			centerPanel.add(nameLabel);
			centerPanel.add(nameResult);
			centerPanel.add(RowLayout.crlf());

			centerPanel.add(idLabel);
			centerPanel.add(idResult);
			centerPanel.add(RowLayout.crlf());
			
			Box hBox = Box.createHorizontalBox();			
			hBox.add(Box.createHorizontalGlue());
			hBox.add(centerPanel);
			hBox.add(Box.createHorizontalGlue());
			add(hBox, BorderLayout.CENTER);
		}
		public void search() {
			String key = searchField.getText();
			PatientIndexEntry entry = Index.getInstance().getInvEntry(key);
			if (entry != null) {
				nameResult.setText(entry.name);
				idResult.setText(entry.id);
			}
			else {
				nameResult.setText("???");
				idResult.setText("???");
			}				
		}
	}

	class RebuildPanel extends JPanel {
		JScrollPane jsp;
		ColorPane cp;
		int margin = 15;

		public RebuildPanel() {
			super();
			setBackground(background);
			setLayout(new BorderLayout());
			add(new HeaderPanel("UID Table Rebuild", 10, 10), BorderLayout.NORTH);
			jsp = new JScrollPane();
			add(jsp, BorderLayout.CENTER);
			cp = new ColorPane();
		}
		public void rebuildUIDIndex() {
			cp.clear();
			Index index = Index.getInstance();
			Configuration config = Configuration.getInstance();
			File storageDir = config.getStorageDir();
			int uidsAdded = 0;
			for (File ptDir : storageDir.listFiles()) {
				if (ptDir.isDirectory()) {
					for (File studyDir : ptDir.listFiles()) {
						boolean found = false;
						if (studyDir.isDirectory() && studyDir.getName().startsWith("Study")) {
							for (File seriesDir : studyDir.listFiles()) {
								for (File seriesFile : seriesDir.listFiles()) {
									FileObject fob = FileObject.getInstance(seriesFile);
									if (fob instanceof DicomObject) {
										DicomObject dob = (DicomObject)fob;
										//update the UID index
										String ptID = dob.getPatientID();
										String stDate = dob.getStudyDate();
										String accNum = dob.getAccessionNumber();
										String uid = dob.getStudyInstanceUID();
										UIDIndexEntry e = index.getUIDIndexEntry(ptID, stDate, accNum);
										if (e == null) {
											index.addStudyInstanceUID(ptID, stDate, accNum, "", uid);
											cp.println(Color.red, ptID + ": " + uid);
											uidsAdded++;
										}
										else cp.println(Color.black, ptID + ": " + uid);
										found = true;
										break;
									}
								} //end of series
								if (found) break;
							}
						}
					} //end of studies
				}
			}//end of patients
			index.commit();
			cp.println("\n" + uidsAdded + " StudyInstanceUID" + ((uidsAdded==1)?"":"s") + " added.");
		}
	}
	
	class CheckPanel extends JPanel {
		JScrollPane jsp;
		ColorPane cp;
		int margin = 15;
		JLabel sts;

		public CheckPanel() {
			super();
			setBackground(background);
			setLayout(new BorderLayout());
			add(new HeaderPanel("Check Indexes", 10, 10), BorderLayout.NORTH);
			jsp = new JScrollPane();
			add(jsp, BorderLayout.CENTER);
			cp = new ColorPane();
			jsp.setViewportView(cp);
			jsp.setViewportView(cp);
			Box ftr = Box.createHorizontalBox();
			sts = new JLabel("Oink Moo Quack");
			ftr.add(Box.createHorizontalStrut(10));
			ftr.add(sts);
			add(ftr, BorderLayout.SOUTH);
		}
		public void checkIndexes() {
			cp.clear();
			//First do the IntegerTable
			Configuration config = Configuration.getInstance();
			IntegerTable table = Configuration.getInstance().getIntegerTable();
			synchronized(table) {
				cp.println(Color.black, "Checking IntegerTable");
				HTree tree = table.index;
				try {
					ExecutorService exec = Executors.newSingleThreadExecutor();
					FastIterator fit = tree.keys();
					int count = 0;
					String key;
					while ( (key = (String)fit.next()) != null ) {
						Future<String> future = exec.submit(new GetterWithTimeout(cp, key, tree, ++count, sts));
						try { cp.println(Color.black, future.get(1, TimeUnit.SECONDS)); }
						catch (TimeoutException e) { cp.println(Color.red, key); }
					}
					cp.println(Color.black, "Finished checking IntegerTable");
					cp.println(Color.black, count + " entries found and retrieved\n");
				}
				catch (Exception ex) {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					cp.println(Color.red, sw.toString());
				}
			}
			
			//Now do the fwdPatientIndex
			Index index = Index.getInstance();
			synchronized(index) {
				cp.println(Color.black, "Checking fwdPatientIndex");
				HTree tree = index.fwdPatientIndex;
				try {
					FastIterator fit = tree.keys();
					int count = 0;
					Object key;
					while ( (key = fit.next()) != null ) {
						Object value = tree.get(key);
						count++;
						final int ct = count;
						final String ks = (String)key;
						final String vs = value.toString();
						Runnable r = new Runnable() {
							public void run() {
								sts.setText(ct + ": " + ks + ": " + vs);
							}
						};
						SwingUtilities.invokeLater(r);
					}
					cp.println(Color.black, "Finished checking fwdPatientIndex");
					cp.println(Color.black, count + " entries found and retrieved\n");
				}
				catch (Exception ex) {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					cp.println(Color.red, sw.toString());
				}
			}
			
			//Now do the invPatientIndex
			synchronized(index) {
				cp.println(Color.black, "Checking invPatientIndex");
				HTree tree = index.invPatientIndex;
				try {
					FastIterator fit = tree.keys();
					int count = 0;
					Object key;
					while ( (key = fit.next()) != null ) {
						Object value = tree.get(key);
						count++;
						final int ct = count;
						final String ks = (String)key;
						final String vs = value.toString();
						Runnable r = new Runnable() {
							public void run() {
								sts.setText(ct + ": " + ks + ": " + vs);
							}
						};
						SwingUtilities.invokeLater(r);
					}
					cp.println(Color.black, "Finished checking invPatientIndex");
					cp.println(Color.black, count + " entries found and retrieved\n");
				}
				catch (Exception ex) {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					cp.println(Color.red, sw.toString());
				}
			}
			
			//Now do the fwdStudyIndex
			synchronized(index) {
				cp.println(Color.black, "Checking fwdStudyIndex");
				HTree tree = index.fwdStudyIndex;
				try {
					FastIterator fit = tree.keys();
					int count = 0;
					Object key;
					while ( (key = fit.next()) != null ) {
						Object value = tree.get(key);
						count++;
						final int ct = count;
						final String ks = (String)key;
						final String vs = value.toString();
						Runnable r = new Runnable() {
							public void run() {
								sts.setText(ct + ": " + ks + ": " + vs);
							}
						};
						SwingUtilities.invokeLater(r);
					}
					cp.println(Color.black, "Finished checking fwdStudyIndex");
					cp.println(Color.black, count + " entries found and retrieved\n");
				}
				catch (Exception ex) {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					cp.println(Color.red, sw.toString());
				}
			}
			
			//Now do the uidIndex
			synchronized(index) {
				cp.println(Color.black, "Checking uidIndex");
				HTree tree = index.uidIndex;
				try {
					FastIterator fit = tree.keys();
					int count = 0;
					Object key;
					while ( (key = fit.next()) != null ) {
						Object value = tree.get(key);
						count++;
						final int ct = count;
						final String ks = (String)key;
						final UIDIndexEntry e = (UIDIndexEntry)value;
						Runnable r = new Runnable() {
							public void run() {
								sts.setText(ct + ": " + ks + ": " + e.origStudyInstanceUID + " - " + e.anonStudyInstanceUID);
							}
						};
						SwingUtilities.invokeLater(r);
					}
					cp.println(Color.black, "Finished checking uidIndex");
					cp.println(Color.black, count + " entries found and retrieved\n");
				}
				catch (Exception ex) {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					cp.println(Color.red, sw.toString());
				}
			}
		}
	}
	
	class GetterWithTimeout implements Callable<String> {
		ColorPane cp;
		String key;
		HTree tree;
		int count;
		JLabel sts;
		public GetterWithTimeout(ColorPane cp, String key, HTree tree, int count, JLabel sts) {
			this.cp = cp;
			this.key = key;
			this.tree = tree;
			this.count = count;
			this.sts = sts;
		}
		public String call() {
			try {
				Object value = tree.get(key);
				final String text = count + ": " + key + ": " + (Integer)value;
				Runnable r = new Runnable() {
					public void run() {
						sts.setText(text);
					}
				};
				SwingUtilities.invokeLater(r);
				return text;
			}
			catch (Exception ex) {
				StringWriter sw = new StringWriter();
				ex.printStackTrace(new PrintWriter(sw));
				cp.println(Color.red, sw.toString());
			}
			return key;
		}
	}
			
	
	class ListPanel extends JPanel {
		JScrollPane jsp;
		java.awt.Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );
		java.awt.Font mono18 = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 18 );
		String[] columnNames = new String[] {
			"ANON-PatientName",
			"ANON-PatientID",
			"PHI-PatientName",
			"PHI-PatientID"
		};
		String[] spreadsheetColumnNames = new String[] {
			"ANON-PatientName",
			"ANON-PatientID",
			"PHI-PatientName",
			"PHI-PatientID",
			"DateOffset",
			"ANON-StudyDate",
			"PHI-StudyDate",
			"ANON-Accession",
			"PHI-Accession",
			"ANON-StudyInstanceUID",
			"PHI-StudyInstanceUID"
		};
		JFileChooser chooser = null;
		int margin = 15;

		public ListPanel() {
			super();
			setBackground(background);
			setLayout(new BorderLayout());
			add(new HeaderPanel("Patient Index List", 10, 10), BorderLayout.NORTH);
			jsp = new JScrollPane();
			add(jsp, BorderLayout.CENTER);
			chooser = new JFileChooser();
			File dir = new File(System.getProperty("user.dir"));
			chooser.setSelectedFile(new File(dir,"Index.xlsx"));
		}
		public void list() {
			PatientIndexEntry[] entries = Index.getInstance().listPatientIndex();
			JPanel panel = new JPanel();
			panel.setLayout(new RowLayout(20,5));
			panel.setBackground(background);
			panel.add(Box.createHorizontalStrut(margin));
			for (int i=0; i<columnNames.length; i++) {
				panel.add(new ColumnLabel(columnNames[i]));
			}
			panel.add(RowLayout.crlf());
			for (int i=0; i<entries.length; i+=2) {
				panel.add(Box.createHorizontalStrut(margin));
				panel.add(new Label(entries[i+1].name));
				panel.add(new Label(entries[i+1].id));
				panel.add(new Label(entries[i].name));
				panel.add(new Label(entries[i].id));
				panel.add(RowLayout.crlf());
			}
			jsp.setViewportView(panel);
			revalidate();
		}
		public void save() {
			Index index = Index.getInstance();
			try {
				PatientIndexEntry[] entries = index.listPatientIndex();
				Workbook wb = new XSSFWorkbook();
				Sheet sheet = wb.createSheet("Index");
				Row row = sheet.createRow((short)0);
				CellStyle style = wb.createCellStyle();
				org.apache.poi.ss.usermodel.Font font = wb.createFont();
				font.setBold(true);
				style.setFont(font);
				for (int i=0; i<spreadsheetColumnNames.length; i++) {
					Cell cell = row.createCell(i);
					cell.setCellValue(spreadsheetColumnNames[i]);
					cell.setCellStyle(style);
				}		
				font.setBold(false);
				style.setFont(font);
				short rowNumber = 1;
				
				for (int i=0; i<entries.length; i+=2) {
					
					//Compute the date offset for this patient
					String incString = AnonymizerFunctions.hash(entries[i].id, -1);
					int n = incString.length();
					if (n > 4) incString = incString.substring( n-4, n);
					long inc = Long.parseLong(incString);
					inc = inc % (10 * 365);
					
					//Get the studies for this patient
					Study[] studies = index.listStudiesFor(entries[i].id);
					
					for (int j=0; j<studies.length; j++) {
						//Put in the patient columns
						row = sheet.createRow(rowNumber++); // was (short)(i/2+1)
						addCell(row, 0, entries[i+1].name, style);
						addCell(row, 1, entries[i+1].id, style);
						addCell(row, 2, entries[i].name, style);
						addCell(row, 3, entries[i].id, style);
						addCell(row, 4, Long.toString(inc), style);
						//Put in the study columns
						addCell(row, 5, studies[j].anonDate, style);
						addCell(row, 6, studies[j].phiDate, style);
						addCell(row, 7, studies[j].anonAccession, style);
						addCell(row, 8, studies[j].phiAccession, style);
						//Put in the StudyInstanceUIDs
						UIDIndexEntry uidEntry = index.getUIDIndexEntry( entries[i+1].id,  studies[j].anonDate,  studies[j].anonAccession);
						if (uidEntry != null) {
							if (uidEntry.anonStudyInstanceUID != null) {
								addCell(row, 9, uidEntry.anonStudyInstanceUID, style);
							}
							if (uidEntry.origStudyInstanceUID != null) {
								addCell(row, 10, uidEntry.origStudyInstanceUID, style);
							}								
						}
					}
				}
				for (int i=0; i<spreadsheetColumnNames.length; i++) sheet.autoSizeColumn(i);
				
				if (chooser.showSaveDialog(this) == chooser.APPROVE_OPTION) {
					File outputFile = chooser.getSelectedFile();
					FileOutputStream fos = new FileOutputStream(outputFile);
					wb.write(fos);
					fos.close();
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		private void addCell(Row row, int k, String text, CellStyle style) {
			Cell cell = row.createCell(k);
			cell.setCellValue(text);
			cell.setCellStyle(style);
		}
		class Label extends JLabel {
			public Label(String text) {
				super(text);
				setFont(mono);
			}
		}
		class ColumnLabel extends JLabel {
			public ColumnLabel(String text) {
				super(text);
				setFont(mono18);
			}
		}
	}
	
	class FooterPanel extends JPanel {
		public JButton search;
		public JButton list;
		public JButton save;
		public JButton check;
		public JButton rebuild;
		public FooterPanel() {
			super();
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createBevelBorder(BevelBorder.LOWERED),
				BorderFactory.createEmptyBorder(2, 2, 2, 2)
			));
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			setBackground(background);
			search = new JButton("Search");
			add(search);
			add(Box.createHorizontalStrut(15));
			list = new JButton(" List ");
			add(list);
			add(Box.createHorizontalStrut(15));
			save = new JButton(" Save ");
			save.setEnabled(false);
			add(save);
			add(Box.createHorizontalGlue());
			check = new JButton(" Check Indexes");
			add(check);
			add(Box.createHorizontalStrut(15));
			rebuild = new JButton(" Rebuild UID Table");
			add(rebuild);
		}
	}
	
}