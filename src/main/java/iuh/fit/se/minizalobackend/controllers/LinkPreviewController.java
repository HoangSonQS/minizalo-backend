package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.response.LinkPreviewResponse;
import iuh.fit.se.minizalobackend.services.LinkPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LinkPreviewController {

    private final LinkPreviewService linkPreviewService;

    @GetMapping("/link-preview")
    public ResponseEntity<LinkPreviewResponse> getLinkPreview(@RequestParam("url") String url) {
        try {
            return ResponseEntity.ok(linkPreviewService.fetchPreview(url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
