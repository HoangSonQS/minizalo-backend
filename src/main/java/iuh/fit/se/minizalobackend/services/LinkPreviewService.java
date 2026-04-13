package iuh.fit.se.minizalobackend.services;

import iuh.fit.se.minizalobackend.dtos.response.LinkPreviewResponse;

public interface LinkPreviewService {
    LinkPreviewResponse fetchPreview(String url);
}
