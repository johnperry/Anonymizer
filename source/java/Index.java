/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;
import org.apache.log4j.Logger;
import org.rsna.util.JdbmUtil;

/**
 * An index of patients, containing PHI and anonymized values for PatientName and PatientID.
 */
public class Index {

	static final Logger logger = Logger.getLogger(Index.class);
	static Index instance = null;

	//Note: fwd means that the key is PHI; inv means the key is anonymized.
    RecordManager recman = null;
    public HTree fwdPatientIndex = null;
    public HTree invPatientIndex = null;
    public HTree fwdStudyIndex = null;
    public HTree uidIndex = null;
    
	/**
	 * Get the singleton instance of the index.
	 * @return the singleton Index object
	 */
    public static synchronized Index getInstance() {
		if (instance == null) {
			instance = new Index();
		}
		return instance;
	}

	protected Index() {
		File databaseDir = Configuration.getInstance().getDatabaseDir();
		File indexFile = new File(databaseDir, "index");
		getIndex(indexFile.getPath());
	}

	/**
	 * Commit the index.
	 */
	public synchronized void commit() {
		if (recman != null) {
			try { recman.commit(); }
			catch (Exception ex) { }
		}
	}

	/**
	 * Commit and close the index.
	 */
	public synchronized void close() {
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception ex) {
				logger.debug("Unable to commit and close the index");
			}
		}
	}

	/**
	 * Add an entry to the  patient index.
	 * @param origPtName the PHI patient name
	 * @param origPtID the PHI patient ID
	 * @param anonPtName the anonymized patient name
	 * @param anonPtID the anonymized patient ID
	 */
	public synchronized void addPatient(String origPtName, String origPtID, String anonPtName, String anonPtID) {
		try {
			PatientIndexEntry fwdEntry = new PatientIndexEntry(origPtID, anonPtName, anonPtID);
			PatientIndexEntry invEntry = new PatientIndexEntry(anonPtID, origPtName, origPtID);
			fwdPatientIndex.put(fwdEntry.key.toLowerCase(), fwdEntry);
			invPatientIndex.put(invEntry.key.toLowerCase(), invEntry);
			commit();
		}
		catch (Exception ex) {
			logger.warn("Unable to access the patient indexes.");
		}
	}
	
	/**
	 * Add an entry to the study index.
	 * @param origPtID the PHI patient ID
	 * @param origStudyDate the PHI study date
	 * @param origAccessionNumber the PHI accession number
	 * @param anonStudyDate the anonymized study date
	 * @param anonAccessionNumber the anonymized accession number
	 */
	public synchronized void addStudy(String origPtID, String origStudyDate, String origAccessionNumber, String anonStudyDate, String anonAccessionNumber) {
		try {
			StudyIndexEntry entry = (StudyIndexEntry) fwdStudyIndex.get(origPtID);
			if (entry == null) entry = new StudyIndexEntry(origPtID);
			Study study = new Study(origStudyDate, origAccessionNumber, anonStudyDate, anonAccessionNumber);
			entry.add(study);
			fwdStudyIndex.put(origPtID, entry);
			commit();
		}
		catch (Exception ex) {
			logger.warn("Unable to access the study index.");
		}
	}
	
	/**
	 * Add an entry to the study UID index.
	 * @param ptID the patient ID (anon)
	 * @param studyDate the study date (anon)
	 * @param accessionNumber the accession number (anon)
	 * @param origStudyInstanceUID the PHI study instance UID
	 * @param anonStudyInstanceUID the anonymized study instance UID
	 */
	public synchronized void addStudyInstanceUID(String ptID, String studyDate, String accessionNumber, String origStudyInstanceUID, String anonStudyInstanceUID) {
		try {
			String key = ptID + "|" + studyDate + "|" + accessionNumber;
			UIDIndexEntry entry = new UIDIndexEntry(origStudyInstanceUID, anonStudyInstanceUID);
			uidIndex.put(key, entry);
			commit();
		}
		catch (Exception ex) {
			logger.warn("Unable to access the UID index.");
		}
	}
	
	/**
	 * Get an entry from the study UID index.
	 * @param ptID the patient ID (anon)
	 * @param studyDate the study date (anon)
	 * @param accessionNumber the accession number (anon)
	 * @return the UIDIndex entry
	 */
	public synchronized UIDIndexEntry getUIDIndexEntry(String ptID, String studyDate, String accessionNumber) {
		try {
			String key = ptID + "|" + studyDate + "|" + accessionNumber;
			return (UIDIndexEntry)uidIndex.get(key);
		}
		catch (Exception ex) { return null; }
	}
	
	/**
	 * Get an entry from the inverse patient index (mapping an anonymized 
	 * PatientID to its PHI PatientIndexEntry.
	 * @param key the anonymized PatientID
	 * @return the PHI PatientIndexEntry corresponding to the anonymized PtName.
	 */
	public synchronized PatientIndexEntry getInvEntry(String key) {
		try { return (PatientIndexEntry)invPatientIndex.get(key.toLowerCase()); }
		catch (Exception ex) { return null; }
	}
	
	/**
	 * Get an entry from the forward patient index (mapping a PHI 
	 * PatientID to its anonymized PatientIndexEntry.
	 * @param key the PHI PatientID
	 * @return the anonymized PatientIndexEntry corresponding to the PHI PtName.
	 */
	public synchronized PatientIndexEntry getFwdEntry(String key) {
		try { return (PatientIndexEntry)fwdPatientIndex.get(key.toLowerCase()); }
		catch (Exception ex) { return null; }
	}
	
	/**
	 * Get an entry from the forward study index.
	 * @param key the PHI PatientID
	 * @return the study index entry for the PHI PatientID
	 */
	public synchronized StudyIndexEntry getFwdStudyEntry(String key) {
		try { return (StudyIndexEntry)fwdStudyIndex.get(key.toLowerCase()); }
		catch (Exception ex) { return null; }
	}

	/**
	 * List the entries in the index, in alphabetical order by anonymized PatientName.
	 * The array consists of pairs of IndexEntries, inv[0], fwd[0], inv[1], fwd[1], etc.
	 */
	public synchronized PatientIndexEntry[] listPatientIndex() {
		try {
			FastIterator fit = invPatientIndex.keys();
			LinkedList<PatientIndexEntry> list = new LinkedList<PatientIndexEntry>();
			Object key;
			while ( (key=fit.next()) != null ) {
				list.add( getInvEntry( (String)key) );
			}
			PatientIndexEntry[] entries = new PatientIndexEntry[list.size()];
			entries = list.toArray(entries);
			Arrays.sort(entries);
			PatientIndexEntry[] invfwd = new PatientIndexEntry[2*entries.length];
			for (int i=0; i<entries.length; i++) {
				invfwd[2*i] = entries[i];
				invfwd[2*i+1] = getFwdEntry(entries[i].id);
			}
			return invfwd;
		}
		catch (Exception ex) { 
			ex.printStackTrace();
			return new PatientIndexEntry[0];
		}
	}
	
	/**
	 * List the studies for a patient, in chronological order by original study date
	 * (which is the same as the order of anonymized study dates).
	 * @param origPatientID the PHI PatientID
	 * @return the array of studies for the patient.
	 */
	public synchronized Study[] listStudiesFor(String origPatientID) {
		try {
			StudyIndexEntry ie = (StudyIndexEntry)fwdStudyIndex.get(origPatientID);
			Study[] studies = ie.studies.toArray(new Study[ie.studies.size()]);
			Arrays.sort(studies);
			return studies;
		}
		catch (Exception ex) { 
			ex.printStackTrace();
			return new Study[0];
		}
	}

	//Load the index HTrees
	private void getIndex(String indexPath) {
		try {
			recman				= JdbmUtil.getRecordManager( indexPath );
			fwdPatientIndex		= JdbmUtil.getHTree(recman, "fwdPatientIndex");
			invPatientIndex		= JdbmUtil.getHTree(recman, "invPatientIndex");
			fwdStudyIndex		= JdbmUtil.getHTree(recman, "fwdStudyIndex");
			uidIndex			= JdbmUtil.getHTree(recman, "uidIndex");
		}
		catch (Exception ex) {
			recman = null;
			logger.warn("Unable to load the indexes.");
		}
	}
}