package com.jobtrackr.user_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Objects;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, Object> {
    
    private String field;
    private String confirmField;
    
    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
        this.field = constraintAnnotation.field();
        this.confirmField = constraintAnnotation.confirmField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        BeanWrapper beanWrapper = new BeanWrapperImpl(value);
        Object fieldValue = beanWrapper.getPropertyValue(field);
        Object confirmFieldValue = beanWrapper.getPropertyValue(confirmField);

        // Let @NotBlank on the individual fields handle null/blank cases.
        if (fieldValue == null || confirmFieldValue == null) {
            return true;
        }
        
        boolean matches = Objects.equals(fieldValue, confirmFieldValue);
        if (!matches) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(confirmField)
                    .addConstraintViolation();
        }
        return matches;
    }
}
