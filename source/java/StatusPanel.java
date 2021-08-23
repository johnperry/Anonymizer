/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * A JPanel to display the status during the processing of files.
 */
public class StatusPanel extends JPanel {
	public JLabel currentFile;
	public JLabel currentStats;
	public StatusPanel(Color background) {
		super();
		this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		this.setBackground(background);
		Border inner = BorderFactory.createEmptyBorder(4, 0, 4, 0);
		Border outer = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		setBorder(BorderFactory.createCompoundBorder(outer, inner));

		currentFile = new JLabel(" ");
		currentStats = new JLabel(" ");

		Box rowB = new Box(BoxLayout.X_AXIS);
		rowB.add(Box.createHorizontalStrut(17));
		rowB.add(currentFile);
		rowB.add(Box.createHorizontalGlue());
		rowB.add(currentStats);
		rowB.add(Box.createHorizontalStrut(17));
		this.add(rowB);
	}
	public void setStatus(int n, String name, long t) {
		final int finalN = n;
		final String finalName = name; 
		final long finalT = t;
		final long finalH = LogPanel.usedMemory()/(1024*1024);
		Runnable r = new Runnable() {
			public void run() {
				String s = String.format("%d: %s ", finalN, finalName);
				String h = String.format("%4d ms  Heap: %d MB", finalT, finalH);
				currentFile.setText(s);
				currentStats.setText(h);
			}
		};
		SwingUtilities.invokeLater(r);
	}
	public void clear() {
		final long finalH = LogPanel.usedMemory()/(1024*1024);
		Runnable r = new Runnable() {
			public void run() {
				String h = String.format("Heap: %d MB", finalH);
				currentFile.setText(" ");
				currentStats.setText(h);
			}
		};
		SwingUtilities.invokeLater(r);
	}
}
