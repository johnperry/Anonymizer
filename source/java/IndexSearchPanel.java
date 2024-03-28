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
import org.apache.log4j.*;
import org.rsna.ui.RowLayout;

/**
 * A JPanel that provides a user interface for searching the Index.
 */
public class IndexSearchPanel extends JPanel implements ActionListener {

	static final Logger logger = Logger.getLogger(IndexSearchPanel.class);

	private SearchPanel searchPanel;
	private FooterPanel footerPanel;
	Color background;

	/**
	 * Class constructor.
	 */
    public IndexSearchPanel() {
		super();
		Configuration config = Configuration.getInstance();
		setLayout(new BorderLayout());
		background = config.background;
		setBackground(background);

		searchPanel = new SearchPanel();
		footerPanel = new FooterPanel();
		footerPanel.search.addActionListener(this);
		searchPanel.searchField.addActionListener(this);
		this.add(searchPanel, BorderLayout.CENTER);
		this.add(footerPanel, BorderLayout.SOUTH);
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
		logger.debug("ActionEvent received: "+source.getClass().getName());
		if (source.equals(footerPanel.search) || source.equals(searchPanel.searchField)) {
			searchPanel.search();
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

	class FooterPanel extends JPanel {
		public JButton search;
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
		}
	}
	
}