package org.obm.push.impl;

import org.obm.annotations.transactional.Propagation;
import org.obm.annotations.transactional.Transactional;
import org.obm.push.backend.IContentsImporter;
import org.obm.push.backend.IErrorsManager;
import org.obm.push.bean.BackendSession;
import org.obm.push.exception.activesync.ProcessingEmailException;
import org.obm.push.exception.activesync.SendEmailException;
import org.obm.push.exception.activesync.SmtpInvalidRcptException;
import org.obm.push.protocol.MailProtocol;
import org.obm.push.protocol.bean.MailRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SmartReplyHandler extends MailRequestHandler {

	@Inject
	protected SmartReplyHandler(IContentsImporter contentsImporter,
			IErrorsManager errorManager, MailProtocol mailProtocol) {
		
		super(contentsImporter, errorManager, mailProtocol);
	}

	@Override
	@Transactional(propagation=Propagation.NESTED)
	public void doTheJob(MailRequest mailRequest, BackendSession bs)
			throws SendEmailException, ProcessingEmailException, SmtpInvalidRcptException {
		
		contentsImporter.replyEmail(bs, mailRequest.getMailContent(), mailRequest.isSaveInSent(),
				Integer.getInteger(mailRequest.getCollectionId()), mailRequest.getServerId());
	}
	
}
