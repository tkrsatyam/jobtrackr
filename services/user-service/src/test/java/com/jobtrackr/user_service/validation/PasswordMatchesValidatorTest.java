package com.jobtrackr.user_service.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordMatchesValidatorTest {

    // A minimal stand-in for any DTO that has password + confirmPassword fields.
    // The validator works via reflection on field names, so this is all it needs.
    record PasswordHolder(String password, String confirmPassword) {}

    @Mock
    private ConstraintValidatorContext context;
    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;
    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    private PasswordMatchesValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordMatchesValidator();

        PasswordMatches annotation = new PasswordMatches() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return PasswordMatches.class; }
            @Override public String message() { return "Passwords do not match"; }
            @Override public String field() { return "password"; }
            @Override public String confirmField() { return "confirmPassword"; }
            @Override public Class<?>[] groups() { return new Class<?>[0]; }
            @Override public Class<? extends jakarta.validation.Payload>[] payload() { return new Class[0]; }
        };

        validator.initialize(annotation);
    }

    @Test
    void matchingPasswordsReturnsTrue() {
        assertThat(validator.isValid(new PasswordHolder("secret123", "secret123"), context)).isTrue();
    }

    @Test
    void nonMatchingPasswordsReturnsFalse() {
        lenient().when(context.getDefaultConstraintMessageTemplate()).thenReturn("Passwords do not match");
        lenient().when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        lenient().when(violationBuilder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertThat(validator.isValid(new PasswordHolder("secret123", "different"), context)).isFalse();
    }

    @Test
    void nullObjectReturnsTrue() {
        // Null-safety: let @NotNull on the DTO handle the null case,
        // not this cross-field validator.
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    void nullPasswordFieldReturnsTrue() {
        // A null individual field is also not this validator's concern —
        // @NotBlank on each field handles it.
        assertThat(validator.isValid(new PasswordHolder(null, "secret123"), context)).isTrue();
    }
}