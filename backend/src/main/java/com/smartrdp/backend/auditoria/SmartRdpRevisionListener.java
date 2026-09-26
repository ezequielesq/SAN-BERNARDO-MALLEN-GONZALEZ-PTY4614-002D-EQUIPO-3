package com.smartrdp.backend.auditoria;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SmartRdpRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        SmartRdpRevisionEntity rev = (SmartRdpRevisionEntity) revisionEntity;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            rev.setUsuarioEmail(auth.getName());
        }
    }
}
