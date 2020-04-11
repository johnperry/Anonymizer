package org.rsna.anonymizer;

import java.io.Serializable;

public class PatientIndexEntry implements Serializable, Comparable<PatientIndexEntry> {
	public String key;
	public String name;
	public String id;
	
	public PatientIndexEntry (String key, String name, String id) {
		this.key = key;
		this.name = name;
		this.id = id;
	}
	
	public int compareTo(PatientIndexEntry ie) {
		return key.compareTo(ie.key);
	}
	
	public String toString() {
		return name + "[" + id + "]";
	}
}
	