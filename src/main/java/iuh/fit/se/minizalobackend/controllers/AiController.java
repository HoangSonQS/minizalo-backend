package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.AiSummarizeRequest;
import iuh.fit.se.minizalobackend.dtos.request.AiPersonaRequest;
import iuh.fit.se.minizalobackend.dtos.request.AiTextRequest;
import iuh.fit.se.minizalobackend.models.MessageDynamo;
import iuh.fit.se.minizalobackend.repository.MessageDynamoRepository;
import iuh.fit.se.minizalobackend.services.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;
    private final MessageDynamoRepository messageDynamoRepository;

    @PostMapping("/{roomId}/ai/summarize")
    public ResponseEntity<Map<String, String>> summarizeChat(
            @PathVariable UUID roomId,
            @Valid @RequestBody AiSummarizeRequest request) {
        
        log.info("Requesting AI summary for room {}, from {} to {}", roomId, request.getStartTime(), request.getEndTime());
        
        List<MessageDynamo> messages = messageDynamoRepository.getMessagesBetweenDates(
                roomId.toString(), 
                request.getStartTime(), 
                request.getEndTime()
        );
        
        String summary = aiService.summarizeChat(roomId.toString(), messages, request.isUnreadOnly());
        
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    @GetMapping("/{roomId}/ai/history")
    public ResponseEntity<List<iuh.fit.se.minizalobackend.models.ChatSummary>> getSummaryHistory(
            @PathVariable UUID roomId) {
        return ResponseEntity.ok(aiService.getSummaryHistory(roomId.toString()));
    }

    @PostMapping("/persona-chat")
    public ResponseEntity<Map<String, String>> askPersona(
            @Valid @RequestBody AiPersonaRequest request) {
        
        log.info("Requesting AI Persona chat for persona: {}", request.getPersona());
        
        String answer = aiService.askPersona(request.getPersona(), request.getQuestion());
        
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    @PostMapping("/translate")
    public ResponseEntity<Map<String, String>> translateText(
            @Valid @RequestBody AiTextRequest request) {
        log.info("Requesting AI translation to: {}", request.getTargetLanguage());
        String targetLang = request.getTargetLanguage() != null ? request.getTargetLanguage() : "Tiếng Việt";
        String result = aiService.translateText(request.getText(), targetLang);
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/improve-text")
    public ResponseEntity<Map<String, String>> improveText(
            @Valid @RequestBody AiTextRequest request) {
        log.info("Requesting AI improve text");
        String result = aiService.improveText(request.getText());
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/{roomId}/ai/extract-events")
    public ResponseEntity<Map<String, String>> extractEvents(
            @PathVariable UUID roomId,
            @Valid @RequestBody AiSummarizeRequest request) {
        
        log.info("Requesting AI extract events for room {}, from {} to {}", roomId, request.getStartTime(), request.getEndTime());
        
        List<MessageDynamo> messages = messageDynamoRepository.getMessagesBetweenDates(
                roomId.toString(), 
                request.getStartTime(), 
                request.getEndTime()
        );
        
        String events = aiService.extractEvents(roomId.toString(), messages);
        
        return ResponseEntity.ok(Map.of("events", events));
    }

    @PostMapping("/speech-to-text")
    public ResponseEntity<Map<String, String>> speechToText(
            @RequestBody Map<String, String> request) {
        
        String base64Audio = request.get("audio");
        String mimeType = request.getOrDefault("mimeType", "audio/mp4");
        
        if (base64Audio == null || base64Audio.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("text", ""));
        }
        
        log.info("Requesting AI speech-to-text, mimeType: {}, audioSize: {} chars", mimeType, base64Audio.length());
        String text = aiService.transcribeAudio(base64Audio, mimeType);
        
        return ResponseEntity.ok(Map.of("text", text));
    }
}
