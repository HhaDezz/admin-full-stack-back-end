package com.atelier.api.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Plugs AuditDataChangeListener into Hibernate once, at startup.
 *
 * Spring hands us the JPA EntityManagerFactory; "unwrap" reaches the Hibernate
 * object underneath it, which owns the list of event listeners.
 */
@Component
public class AuditListenerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AuditListenerRegistrar.class);

    private final EntityManagerFactory entityManagerFactory;
    private final AuditDataChangeListener listener;

    public AuditListenerRegistrar(EntityManagerFactory entityManagerFactory, AuditDataChangeListener listener) {
        this.entityManagerFactory = entityManagerFactory;
        this.listener = listener;
    }

    @PostConstruct
    public void register() {
        if (!listener.isEnabled()) {
            log.info("Audit: data-change logging is switched off (audit.data-changes.enabled=false)");
            return;
        }
        try {
            SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            EventListenerRegistry registry = sessionFactory.getEventEngine().getListenerRegistry();
            registry.getEventListenerGroup(EventType.POST_COMMIT_INSERT).appendListener(listener);
            registry.getEventListenerGroup(EventType.POST_COMMIT_UPDATE).appendListener(listener);
            registry.getEventListenerGroup(EventType.POST_COMMIT_DELETE).appendListener(listener);
            log.info("Audit: data-change logging is on");
        } catch (Exception e) {
            // The application must still start; only this layer is lost.
            log.error("Audit: could not register the data-change listener — DATA rows will not be recorded", e);
        }
    }
}
