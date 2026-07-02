package cool.muyucloud.netherlink.link.nli;

import cool.muyucloud.netherlink.link.model.LinkJoinOperation;
import cool.muyucloud.netherlink.link.model.LinkJoinTarget;
import cool.muyucloud.netherlink.link.official.OfficialJoinService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;

final class NliJoinService implements LinkJoinService {
    private final OfficialJoinService delegate;

    NliJoinService(NliApiClient api, NliRuntimeService runtimes) {
        this.delegate = new OfficialJoinService(runtimeKey -> new NliSignalingClient(
            api,
            runtimes.requireSession(runtimeKey),
            "NetherLink NLI Client Signaling-" + runtimeKey
        ));
    }

    @Override
    public LinkJoinOperation join(String runtimeKey, LinkJoinTarget target) {
        return this.delegate.join(runtimeKey, target);
    }

    @Override
    public boolean hasOutgoingJoin(String runtimeKey) {
        return this.delegate.hasOutgoingJoin(runtimeKey);
    }

    @Override
    public void shutdown(String runtimeKey) {
        this.delegate.shutdown(runtimeKey);
    }

    @Override
    public void shutdown() {
        this.delegate.shutdown();
    }
}
