/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.event.*;
import javax.swing.*;
import org.apache.log4j.Logger;
import org.rsna.ui.ColorPane;
import org.rsna.util.FileUtil;
import org.rsna.ui.ApplicationProperties;

public class LogPanel extends BasePanel implements ActionListener, ChangeListener {

	static final Logger logger = Logger.getLogger(LogPanel.class);

	public static ColorPane out;

	private static final Runtime runtime = Runtime.getRuntime();
	public static long usedMemory() {
		return runtime.totalMemory() - runtime.freeMemory();
	}
	public static long maxMemory() {
		return runtime.maxMemory();
	}
	public static long totalMemory() {
		return runtime.totalMemory();
	}
	private static String memoryLogEntry() {
		String margin = "                                     ";
		StringBuffer sb = new StringBuffer();
		sb.append("Memory:\n");
		sb.append( String.format("%46s%,15d", "IN USE: ", usedMemory()) );
		sb.append("\n");
		sb.append( String.format("%46s%,15d", "TOTAL:  ", totalMemory()) );
		sb.append("\n");
		sb.append( String.format("%46s%,15d", "MAX:    ", maxMemory()) );
		return sb.toString();
}

	JScrollPane jsp;
	JButton delete;
	JButton refresh;
	JCheckBox logMemoryCB;
	boolean logmemory = false;
	File log = new File("logs/anonymizer.log");

	static LogPanel logPanel = null;

	public static synchronized LogPanel getInstance() {
		if (logPanel == null) logPanel = new LogPanel();
		return logPanel;
	}

	protected LogPanel() {
		super();

		out = new ColorPane();
		out.setScrollableTracksViewportWidth(false);

		BasePanel bp = new BasePanel();
		bp.add(out, BorderLayout.CENTER);

		jsp = new JScrollPane();
		jsp.getVerticalScrollBar().setUnitIncrement(10);
		jsp.setViewportView(bp);
		jsp.getViewport().setBackground(Color.white);
		add(jsp, BorderLayout.CENTER);

		delete = new JButton("Delete");
		delete.setToolTipText("Delete old logs");
		delete.addActionListener(this);

		refresh = new JButton("Refresh");
		refresh.addActionListener(this);
		
		logMemoryCB = new JCheckBox("Log memory usage");
		Configuration config = Configuration.getInstance();
		ApplicationProperties props = config.getProps();
		logmemory = props.getProperty("logmemory","").equals("yes");
		logMemoryCB.setSelected(logmemory);
		logMemoryCB.addChangeListener(this);
		logMemoryCB.setBackground(bgColor);

		Box footer = Box.createHorizontalBox();
		footer.add(logMemoryCB);
		footer.add(Box.createHorizontalGlue());
		footer.add(delete);
		footer.add(Box.createHorizontalStrut(3));
		footer.add(refresh);
		add(footer, BorderLayout.SOUTH);
	}

	public void reload() {
		out.clear();
		if (log.exists()) {
			try { out.append( FileUtil.getText( log ) ); }
			catch (Exception ignore) { }
		}
	}
	
	public void logMemory() {
		if (logmemory) {
			logger.info(memoryLogEntry());
		}
	}

	public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(refresh)) {
			reload();
		}
		else if (event.getSource().equals(delete)) {
			File logs = new File("logs");
			File[] files = logs.listFiles();
			for (File file : files) FileUtil.deleteAll(file);
			reload();
		}
	}
	
	public void stateChanged(ChangeEvent event) {
		if (event.getSource().equals(logMemoryCB)) {
			Configuration config = Configuration.getInstance();
			ApplicationProperties props = config.getProps();
			logmemory = logMemoryCB.isSelected();
			props.setProperty("logmemory", logmemory?"yes":"no");
		}
	}

}
