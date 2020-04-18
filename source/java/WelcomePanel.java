package org.rsna.anonymizer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import org.apache.log4j.*;
import org.rsna.ui.RowLayout;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import javax.imageio.*;
import javax.imageio.stream.*;
import java.net.URI;

public class WelcomePanel extends BasePanel {

	static final Logger logger = Logger.getLogger(WelcomePanel.class);
	
	Configuration config;
	Color bg;
	Color rsna = new Color(0, 93, 169);
	Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );
	Font titleFont = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 36 );
	Font textFont = new java.awt.Font( "SansSerif", java.awt.Font.PLAIN, 16 );
	
	static WelcomePanel instance = null;

	public static synchronized WelcomePanel getInstance() {
		if (instance == null) instance = new WelcomePanel();
		return instance;
	}

	protected WelcomePanel() {
		super();
		config = Configuration.getInstance();
		bg = config.background;
		bg = Color.white;
		setLayout(new BorderLayout());
		setBackground(bg);
		
		JPanel message = new JPanel();
		message.setLayout(new RowLayout());
		message.setBackground(bg);
		
		message.add(Box.createVerticalStrut(15));
		message.add(RowLayout.crlf());
		message.add(new LogoPanel());
		message.add(RowLayout.crlf());
		message.add(RowLayout.crlf());
		message.add(new TitleLabel("Welcome", 0.5f));
		message.add(RowLayout.crlf());
		message.add(Box.createVerticalStrut(10));
		message.add(RowLayout.crlf());
		message.add(new MessageArea(messageText));
		message.add(RowLayout.crlf());
		message.add(Box.createVerticalStrut(10));
		message.add(RowLayout.crlf());
		
		JPanel p = new JPanel();
		p.setBackground(bg);
		p.add(message);
		
		JScrollPane jsp = new JScrollPane();
		jsp.getVerticalScrollBar().setUnitIncrement(10);
		jsp.setViewportView(p);
		jsp.getViewport().setBackground(bg);
		add(jsp, BorderLayout.CENTER);
	}

	class TitleLabel extends JLabel {
		public TitleLabel(String text) {
			this(text, 0.0f);
		}
		public TitleLabel(String text, float alignmentX) {
			super(text);
			setFont(titleFont);
			setForeground(rsna);
			setAlignmentX(alignmentX);
		}
	}
	
	class LogoPanel extends JPanel {
		BufferedImage bi = null;
		public LogoPanel() {
			try {
				ImageReader reader = (ImageReader)ImageIO.getImageReadersByFormatName("PNG").next();
				InputStream is = getClass().getResourceAsStream("/RSNAlogo.PNG");
				ImageInputStream iis = ImageIO.createImageInputStream(is);
				reader.setInput(iis, true);
				bi = reader.read(0);
				iis.close();
				is.close();
				Dimension size = new Dimension(bi.getWidth(), bi.getHeight());
				setPreferredSize(size);
				setMinimumSize(size);
				setMaximumSize(size);
			}
			catch (Exception ignore) { }
		}
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(bi,0,0,this);
		}
	}
	
	class MessageArea extends JTextArea implements MouseListener {
		public MessageArea(String text) {
			super();
			setWrapStyleWord(true);
			setLineWrap(true);
			setOpaque(false);
			setBorder(null);
			Dimension d = getSize();
			d.width = 600;
			d.height = 100;
			setColumns(45);
			setFont(textFont);
			setForeground(rsna);
			setAlignmentX(0.5f);
			setEditable(false);
			setText(text);
			addMouseListener(this);
		}
		public void mouseEntered(MouseEvent me) { }
		public void mouseExited(MouseEvent me) { }
		public void mousePressed(MouseEvent me) { }
		public void mouseReleased(MouseEvent me) { }
		public void mouseClicked(MouseEvent me) {
			try {
				int x = me.getX();
				int y = me.getY();
				int offset = viewToModel(new Point(x, y));
				String text = getText();
				int k1 = offset;
				int k2 = offset;
				while ((k1 >= 0) && !Character.isWhitespace(text.charAt(k1))) k1--;
				while ((k2 < text.length()) && !Character.isWhitespace(text.charAt(k2))) k2++;
				if (text.substring(k1+1, k2).equals("here.")) {
					Desktop.getDesktop().browse(new URI("http://www.rsna.org/covid-19"));
				}
			}
			catch (Exception ex) { ex.printStackTrace(); }
		}
	}
	
	String messageText = 
		"RSNA is assembling a repository of de-identified COVID-19-related imaging data for research and education. " +
		"Medical institutions interested in joining this international collaboration are " +
		"invited to submit de-identified imaging data for inclusion in this repository. " +
		"The RSNA Anonymizer program is a free open-source tool for collecting and de-identifying DICOM studies " +
		"to prepare them for submission. It may be used to ensure privacy by removing protected " +
		"health information from your DICOM imaging studies." +
		"\n\n" +
		"You can learn more about the RSNA COVID-19 Imaging Data Repository initiative here.";
}
