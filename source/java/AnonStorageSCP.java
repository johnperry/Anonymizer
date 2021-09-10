/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.dicom;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.log4j.Logger;
import org.dcm4che.data.FileMetaInfo;
import org.rsna.ctp.stdstages.dicom.AnonStorageSCP;
import org.rsna.ctp.stdstages.dicom.SimpleDicomStorageSCP;
import org.rsna.util.FileUtil;

public class AnonStorageSCP extends SimpleDicomStorageSCP {
	
    final static Logger logger = Logger.getLogger(AnonStorageSCP.class);

    public AnonStorageSCP(File directory, int port) {
		super(directory, port);
	}
	
    //Store the object in the directory.
    void storeToDir(InputStream in,
    						FileMetaInfo fmi,
    						String name, String callingAET) throws IOException {
								
		File savedFile = new File(directory, name);
		OutputStream out = null;
        try {
			out = new BufferedOutputStream(new FileOutputStream(savedFile));
            fmi.write(out);
            copy(in, out, -1);
            out.close();
            out = null;
        }
        catch (Exception ex) {
			logger.warn("Unable to store a received file.",ex);
			savedFile = null;
		}
        finally { FileUtil.close(out); }
        if (savedFile != null) sendFileEvent(savedFile, callingAET);
    }
}	