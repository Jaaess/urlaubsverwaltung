package org.synyx.urlaubsverwaltung.web.validator;

import org.springframework.stereotype.Component;

import org.springframework.util.StringUtils;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.synyx.urlaubsverwaltung.web.sicknote.SickNoteConvertForm;


/**
 * Class for validating {@link org.synyx.urlaubsverwaltung.web.sicknote.SickNoteConvertForm} object.
 *
 * @author  Aljona Murygina - murygina@synyx.de
 */
@Component
public class SickNoteConvertFormValidator implements Validator {

    private static final String ERROR_MANDATORY_FIELD = "error.mandatory.field";
    private static final String ERROR_LENGTH = "error.length";

    private static final int MAX_CHARS = 200;

    @Override
    public boolean supports(Class<?> clazz) {

        return SickNoteConvertForm.class.equals(clazz);
    }


    @Override
    public void validate(Object target, Errors errors) {

        SickNoteConvertForm convertForm = (SickNoteConvertForm) target;

        String reason = convertForm.getReason();

        if (!StringUtils.hasText(reason)) {
            errors.rejectValue("reason", ERROR_MANDATORY_FIELD);
        } else {
            if (reason.length() > MAX_CHARS) {
                errors.rejectValue("reason", ERROR_LENGTH);
            }
        }
    }
}
