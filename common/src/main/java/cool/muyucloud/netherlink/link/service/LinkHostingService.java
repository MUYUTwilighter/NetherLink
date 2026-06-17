package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkHostPublication;

import java.time.Duration;

public interface LinkHostingService {
    LinkHostPublication publish(String hostKey, Duration signalingReadyTimeout);
}
