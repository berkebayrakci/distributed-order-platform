package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.model.*;
import com.berke.orders.orchestrator.repo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TraceLogService {
    private final InterfaceLogRepository logRepo;
    private final TraceEventRepository traceRepo;
    private final SequenceRepository seqRepo;
    private final ObjectMapper om = new ObjectMapper();

    public void log(Long operationId, String iface, String dir, String status, Object req, Object res, String err) {
        log(operationId, null, iface, dir, status, req, res, err);
    }

    public void log(Long operationId, UUID correlationId, String iface, String dir, String status,
                    Object req, Object res, String err) {
        int step = seqRepo.nextStep().intValue();
        String tid = operationId + "-" + step;
        traceRepo.save(OperationTraceEvent.builder().operationId(operationId).correlationId(correlationId).traceEventId(tid).stepNo(step).description(iface + " " + dir).build());
        logRepo.save(InterfaceLog.builder().operationId(operationId).correlationId(correlationId).traceEventId(tid).stepNo(step).interfaceName(iface).direction(dir).status(status).requestPayload(json(req)).responsePayload(json(res)).errorMessage(err).build());
    }

    private String json(Object o) {
        try {
            return o == null ? null : om.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
