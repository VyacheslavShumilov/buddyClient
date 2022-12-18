package mr.shtein.util.validator

import mr.shtein.data.exception.ValidationException

class IdentificationNumberValidator: Validator {

    companion object {
        private var IDENTIFICATION_NUMBER_EXCEPTION = "ИНН должен состоять из 10 цифр"
    }

    override fun validateValue(valueForValidate: String): Boolean {
        return isValidIdentificationNumber(valueForValidate)
    }

    private fun isValidIdentificationNumber(number: String): Boolean {
        if (number.length != 10) throw ValidationException(IDENTIFICATION_NUMBER_EXCEPTION)
        return true
    }
}