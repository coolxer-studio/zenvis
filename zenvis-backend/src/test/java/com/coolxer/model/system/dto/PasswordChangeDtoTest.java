package com.coolxer.model.system.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangeDtoTest {

    @Test
    void acceptsEncryptedPasswordPayloadBeforePlaintextValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            PasswordChangeDto dto = new PasswordChangeDto();
            dto.setPassword("A".repeat(172));
            dto.setOldPassword("B".repeat(172));

            assertThat(validator.validate(dto)).isEmpty();
        }
    }

    @Test
    void rejectsBlankPasswordPayload() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            PasswordChangeDto dto = new PasswordChangeDto();
            dto.setPassword(" ");
            dto.setOldPassword("encrypted-old-password");

            assertThat(validator.validate(dto))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("password");
        }
    }
}
