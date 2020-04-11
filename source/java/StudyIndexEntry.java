package org.rsna.anonymizer;

import java.io.Serializable;
import java.util.HashSet;

public class StudyIndexEntry implements Serializable, Comparable<StudyIndexEntry> {
	public String key; // PHI PatientID
	public HashSet<Study> studies;
	
	public StudyIndexEntry(String key) {
		this.key = key;
		this.studies = new HashSet<Study>();
	}
	
	public void add(Study study) {
		studies.add(study);
	}
	
	public int compareTo(StudyIndexEntry ie) {
		return key.compareTo(ie.key);
	}
}