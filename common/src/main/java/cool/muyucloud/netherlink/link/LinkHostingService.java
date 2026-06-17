package cool.muyucloud.netherlink.link;

import java.time.Duration;

public interface LinkHostingService {
    LinkHostPublication publish(LinkHostContext context, Duration signalingReadyTimeout);
}
