/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.File;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.UIDDictionary;
import org.dcm4che.net.AAssociateAC;
import org.dcm4che.net.AAssociateRQ;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.FutureRSP;
import org.dcm4che.net.PDU;
import org.dcm4che.util.DcmURL;
import org.dcm4che.util.SSLContextAdapter;

import org.apache.log4j.*;

public class DicomQRSCU {
	
    private final static int PCID_FIND = 1;
    private final static int PCID_MOVE = 3;
    private final static int PCID_GET = 5;
    private final static String STUDY_LABEL = "STUDY";
    private final static String IMAGE_LABEL = "IMAGE";
    private final static String ENHANCED_LABEL = "ENHANCED";
    private final static String[] TS = {
		UIDs.ExplicitVRLittleEndian,
		UIDs.ImplicitVRLittleEndian
	};

    private final static Logger logger = Logger.getLogger(DicomQRSCU.class);
    private final static AssociationFactory af = AssociationFactory.getInstance();
    private final static DcmObjectFactory dof = DcmObjectFactory.getInstance();

	private static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	private static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();
	private static final UIDDictionary uidDictionary = dFact.getDefaultUIDDictionary();

    private DcmURL url = null;
    private int priority = Command.MEDIUM;
    private int acTimeout = 5000;
    private int dimseTimeout = 0;
    private int soCloseDelay = 500;
    private int maxPduLen = 16384;
    private AAssociateRQ assocRQ = af.newAAssociateRQ();
    private boolean packPDVs = false;
    private SSLContextAdapter tls = null;
    private String[] cipherSuites = null;
    private Dataset keys = null;
    private Association assoc = null;
    private ActiveAssociation aassoc = null;

    /**
     * DicomQRSCP constructor
     * @param  url  the QR SCP URL
     */
    public DicomQRSCU(String url) {
		this(new DcmURL(url));
	}
	
    /**
     * DicomQRSCP constructor
     * @param url the QR SCP URL
     */
    public DicomQRSCU(DcmURL url) {
        this.url = url;
        priority = 0;
        initAssocParam(url);
        initTLS();
    }

    private static String maskNull(String aet) {
        return aet != null ? aet : "CTPQRSCU";
    }

    private final void initAssocParam(DcmURL url) {
        assocRQ.setCalledAET(url.getCalledAET());
        assocRQ.setCallingAET(maskNull(url.getCallingAET()));
        assocRQ.setMaxPDULength(maxPduLen);
        assocRQ.addPresContext(af.newPresContext(PCID_FIND,
                UIDs.StudyRootQueryRetrieveInformationModelFIND, TS));
        assocRQ.addPresContext(af.newPresContext(PCID_MOVE,
                UIDs.StudyRootQueryRetrieveInformationModelMOVE, TS));
        assocRQ.addPresContext(af.newPresContext(PCID_GET,
                UIDs.StudyRootQueryRetrieveInformationModelGET, TS));
    }

    private void initTLS() {
        try {
            cipherSuites = url.getCipherSuites();
            if (cipherSuites != null) {
				tls = SSLContextAdapter.getInstance();
				char[] keypasswd = "secret".toCharArray();
				tls.setKey(
						tls.loadKeyStore("certificates/test_sys_1.p12",keypasswd),
						keypasswd);
				tls.setTrust(
						tls.loadKeyStore("certificates/mesa_certs.jks",keypasswd));
				tls.init();
			}
        } 
        catch (Exception ex) {
            throw new RuntimeException("Could not initalize TLS configuration: ", ex);
        }
    }

	private void initKeys(Hashtable<String,String>query) {
		keys = dof.newDataset();
		keys.putCS(Tags.QueryRetrieveLevel, STUDY_LABEL);
        keys.putUS(Tags.NumberOfStudyRelatedSeries);
        keys.putUS(Tags.NumberOfStudyRelatedInstances);
        keys.putUI(Tags.StudyInstanceUID);
        keys.putLO(Tags.PatientID);
        keys.putPN(Tags.PatientName);
        keys.putPN(Tags.Modality);
        keys.putDA(Tags.StudyDate);
        for (String name : query.keySet()) {
			keys.putXX(Tags.forName(name), query.get(name));
		}
	}

    /**
     * Query the SCP at the Study level.
     */
    public List doStudyRootQuery( Hashtable<String,String> keyTable ) throws Exception {
		initKeys(keyTable);
		FutureRSP future = null;
		if (aassoc == null) {  throw new Exception("No Association established"); }
		Command rqCmd = dof.newCommand();
		rqCmd.initCFindRQ(assoc.nextMsgID(),
				UIDs.StudyRootQueryRetrieveInformationModelFIND, priority);
		Dimse findRq = af.newDimse(PCID_FIND, rqCmd, keys);
		findRq.getDataset().putCS(Tags.QueryRetrieveLevel, STUDY_LABEL);
		log("About to send C_FIND with dataset:", findRq.getDataset());
		future = aassoc.invoke(findRq);
		Dimse findRsp = future.get();
		return future.listPending();
    }

    public int doMove(Dataset findRspDs, String dest) throws Exception {
        String suid = findRspDs.getString(Tags.StudyInstanceUID);
        String patName = findRspDs.getString(Tags.PatientName);
        String patID = findRspDs.getString(Tags.PatientID);
        String studyDate = findRspDs.getString(Tags.StudyDate);
        log("Constructing C-MOVE-RQ from findRspDs:", findRspDs);
        Command rqCmd = dof.newCommand();
        rqCmd.initCMoveRQ(assoc.nextMsgID(),
                UIDs.StudyRootQueryRetrieveInformationModelMOVE,
                priority,
                dest);
        Dataset rqDs = dof.newDataset();
        rqDs.putCS(Tags.QueryRetrieveLevel, STUDY_LABEL);
        rqDs.putCS(Tags.QueryRetrieveView, ENHANCED_LABEL);
        rqDs.putUI(Tags.StudyInstanceUID, suid);
        Dimse moveRq = af.newDimse(PCID_MOVE, rqCmd, rqDs);
        log("About to send moveRq with rqDs:", rqDs);
        FutureRSP future = aassoc.invoke(moveRq);
        Dimse moveRsp = future.get();
        Command rspCmd = moveRsp.getCommand();
        int status = rspCmd.getStatus(); //0x0000=OK, 0xB000=Failed
        return status;
    }

    public int doGet(Dataset findRspDs) throws Exception {
        String suid = findRspDs.getString(Tags.StudyInstanceUID);
        String patName = findRspDs.getString(Tags.PatientName);
        String patID = findRspDs.getString(Tags.PatientID);
        String studyDate = findRspDs.getString(Tags.StudyDate);
        log("Constructing C-GET-RQ from findRspDs:", findRspDs);
        Command rqCmd = dof.newCommand();
        rqCmd.initCGetRQ(assoc.nextMsgID(),
                UIDs.StudyRootQueryRetrieveInformationModelGET,
                priority);
        Dataset rqDs = dof.newDataset();
        rqDs.putCS(Tags.QueryRetrieveLevel, STUDY_LABEL);
        rqDs.putCS(Tags.QueryRetrieveView, ENHANCED_LABEL);
        rqDs.putUI(Tags.StudyInstanceUID, suid);
        Dimse getRq = af.newDimse(PCID_GET, rqCmd, rqDs);
        log("About to send getRq with rqDs:", rqDs);
        FutureRSP future = aassoc.invoke(getRq);
        Dimse getRsp = future.get();
        Command rspCmd = getRsp.getCommand();
        log("Received response with Command:", rspCmd);
        log("getRsp.getDataset:", getRsp.getDataset());
        int status = rspCmd.getStatus(); //0x0000=OK, 0xB000=Failed
        return status;
    }
    
    private void log(String title, Dataset ds) {
		if (logger.isDebugEnabled()) {
			if (ds == null) logger.info(title+ " ds is null");
			else {
				logger.info(title);
				for (Iterator it=ds.iterator(); it.hasNext(); ) {
					DcmElement el = (DcmElement)it.next();
					int tag = el.tag();
					String tagString = Tags.toString(tag);
					String tagName;
					String name = "";
					try { name = tagDictionary.lookup(tag).name; }
					catch (Exception ignore) { }
					int vr = el.vr();
					String vrString = VRs.toString(vr);
					String value;
					if (vrString.equals("SQ")) value = "SQ";
					else value = ds.getString(tag);
					logger.info("    "+tagString + " ["+name+"] " + value);
				}
			}
		}
	}
    private void log(String title, Command cmd) {
		if (logger.isDebugEnabled()) {
			if (cmd == null) logger.info(title+ " cmd is null");
			else {
				logger.info(title);
				logger.info("    CommandField: "+cmd.getCommandField());
				logger.info("    MessageID: "+cmd.getMessageID());
				logger.info("    AffectedSopClassUID: "+cmd.getAffectedSOPClassUID());
				logger.info("    RequestedSOPClassUID: "+cmd.getRequestedSOPClassUID());
				logger.info("    AffectedSOPInstanceUID: "+cmd.getAffectedSOPInstanceUID());
				logger.info("    RequestedSOPInstanceUID: "+cmd.getRequestedSOPInstanceUID());
				logger.info("    Status: "+Integer.toHexString(cmd.getStatus()));
			}
		}
	}

    /**
     * Open the association
     * @param logText text to log before issuing the open
     * @return true if the association was created; false otherwise.
     */
    public boolean open(String logText) throws IOException, GeneralSecurityException {
		logger.info(logText);
		return open();
	}
	
    /**
     * Open the association
     * @return true if the association was created; false otherwise.
     */
    public boolean open() throws IOException, GeneralSecurityException {
        if (aassoc != null) throw new IllegalStateException("Association already established");
        assoc = af.newRequestor(newSocket(url.getHost(), url.getPort()));
        assoc.setAcTimeout(acTimeout);
        assoc.setDimseTimeout(dimseTimeout);
        assoc.setSoCloseDelay(soCloseDelay);
        assoc.setPackPDVs(packPDVs);

        PDU assocAC = assoc.connect(assocRQ);
        if (!(assocAC instanceof AAssociateAC)) {
            assoc = null;
            return false;
        }
        aassoc = af.newActiveAssociation(assoc, null);
        aassoc.start();
        return true;
    }

    /**
     * Close the association
     */
    public void close() throws InterruptedException, IOException {
        if (assoc != null) {
            try { aassoc.release(false); }
            finally {
                assoc = null;
                aassoc = null;
            }
        }
    }

    private Socket newSocket(String host, int port) throws IOException, GeneralSecurityException {
        if (cipherSuites != null) {
            return tls.getSocketFactory(cipherSuites).createSocket(host, port);
        } 
        else return new Socket(host, port);
    }
}
