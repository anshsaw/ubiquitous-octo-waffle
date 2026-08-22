package com.portfoliopilot.service.admin;

import com.portfoliopilot.dto.admin.TemplateRequest;
import com.portfoliopilot.dto.portfolio.TemplateResponse;
import com.portfoliopilot.exception.BusinessValidationException;
import com.portfoliopilot.exception.DuplicateResourceException;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.PortfolioTemplate;
import com.portfoliopilot.model.embedded.ThemeSettings;
import com.portfoliopilot.model.enums.AdminAction;
import com.portfoliopilot.model.enums.PortfolioSection;
import com.portfoliopilot.repository.PortfolioRepository;
import com.portfoliopilot.repository.PortfolioTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin CRUD for portfolio templates.
 *
 * <p>Two rules protect existing portfolios:
 * <ol>
 *   <li>{@code templateKey} is immutable once set - it maps to a renderer, and
 *       renaming it would break every portfolio using that layout;</li>
 *   <li>a template still referenced by a portfolio cannot be hard-deleted. The
 *       admin is told to deactivate it instead, which removes it from the picker
 *       while existing portfolios keep rendering.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTemplateService {

    private final PortfolioTemplateRepository templateRepository;
    private final PortfolioRepository portfolioRepository;
    private final AuditService auditService;

    /** Admins see inactive templates too - that is the point of the screen. */
    public List<TemplateResponse> listAll() {
        return templateRepository.findAllByOrderBySortOrderAsc().stream()
                .map(TemplateResponse::from)
                .toList();
    }

    public TemplateResponse create(TemplateRequest request) {
        if (templateRepository.existsByTemplateKey(request.templateKey())) {
            throw new DuplicateResourceException(
                    "A template with key '" + request.templateKey() + "' already exists");
        }
        validateSections(request);

        Instant now = Instant.now();
        PortfolioTemplate template = PortfolioTemplate.builder()
                .name(request.name().trim())
                .description(request.description())
                .templateKey(request.templateKey())
                .thumbnailUrl(request.thumbnailUrl())
                .previewUrl(request.previewUrl())
                .availableSections(new ArrayList<>(request.availableSections()))
                .defaultSections(request.defaultSections() == null
                        ? new ArrayList<>(request.availableSections())
                        : new ArrayList<>(request.defaultSections()))
                .theme(theme(request))
                .sortOrder(request.sortOrder() == null ? 100 : request.sortOrder())
                .active(request.active() == null || request.active())
                .createdAt(now)
                .updatedAt(now)
                .build();

        PortfolioTemplate saved = templateRepository.save(template);
        auditService.record(AdminAction.CREATE_TEMPLATE, null, "portfolioTemplates", saved.getId(),
                Map.of("templateKey", saved.getTemplateKey(), "name", saved.getName()));

        return TemplateResponse.from(saved);
    }

    public TemplateResponse update(String templateId, TemplateRequest request) {
        PortfolioTemplate template = require(templateId);
        validateSections(request);

        if (!template.getTemplateKey().equals(request.templateKey())) {
            long inUse = portfolioRepository.countByTemplateIdAndDeletedFalse(templateId);
            if (inUse > 0) {
                throw new BusinessValidationException(
                        "templateKey cannot be changed: %d portfolio(s) render with it".formatted(inUse),
                        Map.of("templateKey", "Immutable while portfolios reference this template"));
            }
            template.setTemplateKey(request.templateKey());
        }

        template.setName(request.name().trim());
        template.setDescription(request.description());
        template.setThumbnailUrl(request.thumbnailUrl());
        template.setPreviewUrl(request.previewUrl());
        template.setAvailableSections(new ArrayList<>(request.availableSections()));
        if (request.defaultSections() != null) {
            template.setDefaultSections(new ArrayList<>(request.defaultSections()));
        }
        template.setTheme(theme(request));
        if (request.sortOrder() != null) {
            template.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            template.setActive(request.active());
        }
        template.setUpdatedAt(Instant.now());

        PortfolioTemplate saved = templateRepository.save(template);
        auditService.record(AdminAction.UPDATE_TEMPLATE, null, "portfolioTemplates", saved.getId(),
                Map.of("templateKey", saved.getTemplateKey()));

        return TemplateResponse.from(saved);
    }

    /** Soft-disable. Removes it from the picker; portfolios already using it keep working. */
    public TemplateResponse setActive(String templateId, boolean active) {
        PortfolioTemplate template = require(templateId);
        template.setActive(active);
        template.setUpdatedAt(Instant.now());

        PortfolioTemplate saved = templateRepository.save(template);
        auditService.record(
                active ? AdminAction.UPDATE_TEMPLATE : AdminAction.DEACTIVATE_TEMPLATE,
                null, "portfolioTemplates", templateId,
                Map.of("active", active));

        return TemplateResponse.from(saved);
    }

    /** Hard delete, permitted only when nothing references the template. */
    public void delete(String templateId) {
        PortfolioTemplate template = require(templateId);

        long inUse = portfolioRepository.countByTemplateIdAndDeletedFalse(templateId);
        if (inUse > 0) {
            throw new BusinessValidationException(
                    "Cannot delete: %d portfolio(s) use this template. Deactivate it instead.".formatted(inUse));
        }

        templateRepository.delete(template);
        auditService.record(AdminAction.DELETE_TEMPLATE, null, "portfolioTemplates", templateId,
                Map.of("templateKey", template.getTemplateKey()));

        log.warn("Admin hard-deleted unused template {} ({})", templateId, template.getTemplateKey());
    }

    private PortfolioTemplate require(String templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> ResourceNotFoundException.of("Template"));
    }

    /** Section keys must be from the known set, or the builder would render nothing. */
    private void validateSections(TemplateRequest request) {
        for (String section : request.availableSections()) {
            try {
                PortfolioSection.fromKey(section);
            } catch (IllegalArgumentException ex) {
                throw new BusinessValidationException("Unknown section: " + section,
                        Map.of("availableSections",
                                "Allowed: about, skills, projects, education, experience, certificates, contact"));
            }
        }
    }

    private ThemeSettings theme(TemplateRequest request) {
        if (request.primaryColor() == null && request.accentColor() == null && request.darkMode() == null) {
            return null;
        }
        return ThemeSettings.builder()
                .primaryColor(request.primaryColor())
                .accentColor(request.accentColor())
                .darkMode(request.darkMode())
                .build();
    }
}
