export type CriterionValueState = {
  value_list: string[];
  value_text: string;
};

export const isValuelessOperator = (operator: string) => {
  return operator === 'isnull' || operator === 'isnotnull';
};

const splitValueText = (valueText: string) => {
  return valueText
    .split(/\r?\n|,/)
    .map(value => value.trim())
    .filter(Boolean);
};

export const valuesAfterOperatorChange = (
  operator: string,
  state: CriterionValueState,
): CriterionValueState => {
  const currentValues = [...(state.value_list || [])];
  const textValues = splitValueText(state.value_text || '');
  const firstValue = currentValues.find(value => value !== '') || textValues[0] || '';

  if (isValuelessOperator(operator)) {
    return {
      value_list: currentValues,
      value_text: state.value_text || firstValue,
    };
  }

  if (operator === 'between') {
    return {
      value_list: [currentValues[0] || firstValue, currentValues[1] || ''],
      value_text: state.value_text || firstValue,
    };
  }

  if (operator === 'in') {
    const valueText =
      currentValues.length > 1
        ? currentValues.filter(Boolean).join('\n')
        : state.value_text || firstValue;
    return {
      value_list: splitValueText(valueText),
      value_text: valueText,
    };
  }

  return {
    value_list: [firstValue],
    value_text: firstValue,
  };
};

export const valuesForRequest = (operator: string, valueList: string[]) => {
  return isValuelessOperator(operator) ? [] : [...(valueList || [])];
};
