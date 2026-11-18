package com.gtech.automaticbroker.enginebroker.api;

import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderCommunicationException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderTimeoutException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderUnavailableException;
import com.gtech.automaticbroker.enginebroker.util.exception.DatabaseServiceException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidAiResponseException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidResearchEvidenceException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidResearchRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, InvalidResearchRequestException.class})
    ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "The research request is invalid."));
    }

    @ExceptionHandler(AiProviderUnavailableException.class)
    ResponseEntity<ApiError> aiUnavailable(AiProviderUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("AI_PROVIDER_UNAVAILABLE", "The configured AI provider is unavailable."));
    }

    @ExceptionHandler(AiProviderTimeoutException.class)
    ResponseEntity<ApiError> aiTimeout(AiProviderTimeoutException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiError("AI_PROVIDER_TIMEOUT", "The AI provider request timed out."));
    }

    @ExceptionHandler(AiProviderCommunicationException.class)
    ResponseEntity<ApiError> aiCommunicationFailure(AiProviderCommunicationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AI_PROVIDER_COMMUNICATION_FAILED", "Communication with the AI provider failed."));
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<ApiError> aiProviderFailure(AiProviderException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AI_PROVIDER_COMMUNICATION_FAILED", "Communication with the AI provider failed."));
    }

    @ExceptionHandler(InvalidAiResponseException.class)
    ResponseEntity<ApiError> invalidAiResponse(InvalidAiResponseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("INVALID_AI_RESPONSE", "The AI provider returned an invalid research result."));
    }

    @ExceptionHandler(InvalidResearchEvidenceException.class)
    ResponseEntity<ApiError> invalidResearchEvidence(InvalidResearchEvidenceException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("INVALID_RESEARCH_EVIDENCE", "A configured source returned invalid evidence."));
    }

    @ExceptionHandler(DatabaseServiceException.class)
    ResponseEntity<ApiError> databaseUnavailable(DatabaseServiceException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("PERSISTENCE_UNAVAILABLE", "manageDatabase could not complete the request."));
    }

    public record ApiError(String code, String message) {
    }
}
