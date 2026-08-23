package com.portfoliopilot.service;

import com.portfoliopilot.dto.portfolio.TemplateResponse;
import com.portfoliopilot.exception.BusinessValidationException;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.PortfolioTemplate;
import com.portfoliopilot.repository.PortfolioTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Read access to the shared template catalogue. Admin CRUD lives in {@code AdminTemplateService}. */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final PortfolioTemplateRepository templateRepository;

    /** The /builder picker shows active templates only. */
    public List<TemplateResponse> listActive() {
        return templateRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(TemplateResponse::from)
                .toList();
    }

    public PortfolioTemplate requireById(String templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> ResourceNotFoundException.of("Template"));
    }

    public PortfolioTemplate requireByKey(String templateKey) {
        return templateRepository.findByTemplateKey(templateKey)
                .orElseThrow(() -> ResourceNotFoundException.of("Template '" + templateKey + "'"));
    }

    /**
     * Resolves a template from either identifier, falling back to the first
     * active one.
     *
     * <p>An inactive template can still be resolved BY ID, because an existing
     * portfolio must keep rendering after an admin retires its layout. Only new
     * selections are restricted to active templates.
     */
    public PortfolioTemplate resolve(String templateId, String templateKey) {
        if (templateId != null && !templateId.isBlank()) {
            return requireById(templateId);
        }
        if (templateKey != null && !templateKey.isBlank()) {
            return requireByKey(templateKey);
        }
        return templateRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        "No active portfolio templates exist. Seed the database first: cd mongodb && npm run seed"));
    }
}
