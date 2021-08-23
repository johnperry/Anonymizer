/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.awt.*;
import javax.swing.*;
import org.rsna.ui.ColorPane;

//Class to display the results of the processing
class ResultsScrollPane extends JScrollPane {
	public ColorPane text;
	String margin = "       ";
	
	public ResultsScrollPane() {
		this("       ");
	}	
	public ResultsScrollPane(String margin) {
		super();
		this.margin = margin;
		text = new ColorPane();
		setViewportView(text);
		getVerticalScrollBar().setUnitIncrement(10);
	}
	
	public void clear() {
		text.setText("");
	}
	public void newItem(int count, String s) {
		text.print(Color.black, String.format("%5d: %s\n", count, s));
	}
	public void print(Color c, String s) {
		text.print(c, margin + s);
	}
	public void println(String s) {
		text.print(Color.black, margin + s + "\n");
	}
	public void println(Color c, String s) {
		text.print(c, margin + s + "\n");
	}
}
