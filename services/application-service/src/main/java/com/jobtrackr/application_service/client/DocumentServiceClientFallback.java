package com.jobtrackr.application_service.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DocumentServiceClientFallback implements DocumentServiceClient{

    @Override
    public List<Map<String, Object>> getLinkedDocuments(UUID applicationId, String userId) {
        return Collections.emptyList();
    }
}
