/*---------------------------------------------------------------
*  Copyright 2021 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.io.Serializable;

public class UIDIndexEntry implements Serializable {
	
	public String origStudyInstanceUID;
	public String anonStudyInstanceUID;
	
	public UIDIndexEntry(String origStudyInstanceUID, String anonStudyInstanceUID) {
		this.origStudyInstanceUID = origStudyInstanceUID;
		this.anonStudyInstanceUID = anonStudyInstanceUID;
	}
}