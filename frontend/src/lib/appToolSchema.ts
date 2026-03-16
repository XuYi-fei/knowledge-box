export type AppToolFieldType = 'text' | 'textarea' | 'password' | 'number' | 'select' | 'switch';

export type AppToolFieldOption = {
  label: string;
  value: string;
};

export type AppToolFieldDefaultValue = string | number | boolean | null;
export type AppToolFormValue = string | number | boolean | undefined;

export type AppToolFieldSchema = {
  name: string;
  label: string;
  type: AppToolFieldType;
  required?: boolean;
  maxLength?: number;
  placeholder?: string;
  description?: string;
  rows?: number;
  min?: number;
  max?: number;
  step?: number;
  options?: AppToolFieldOption[];
  defaultValue?: AppToolFieldDefaultValue;
};

export type AppToolInputSchema = {
  fields: AppToolFieldSchema[];
};

export type AppToolResultDisplayMode = 'text' | 'json';

export type AppToolResultSchema = {
  displayMode?: AppToolResultDisplayMode;
  primaryField?: string;
  copyable?: boolean;
  rows?: number;
};

const SUPPORTED_FIELD_TYPES = new Set<AppToolFieldType>(['text', 'textarea', 'password', 'number', 'select', 'switch']);

function asObject(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function normalizeString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function normalizeNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function normalizeBoolean(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

function normalizeFieldDefaultValue(type: AppToolFieldType, value: unknown): AppToolFieldDefaultValue {
  if (value === null || value === undefined) {
    return null;
  }
  if (type === 'number') {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
  }
  if (type === 'switch') {
    return typeof value === 'boolean' ? value : null;
  }
  if (type === 'text' || type === 'textarea' || type === 'password' || type === 'select') {
    return typeof value === 'string' ? value : null;
  }
  return null;
}

export function parseAppToolInputSchema(raw: string | undefined): AppToolInputSchema {
  try {
    const parsed = raw ? (JSON.parse(raw) as unknown) : {};
    const objectValue = asObject(parsed);
    if (!objectValue || !Array.isArray(objectValue.fields)) {
      return { fields: [] };
    }
    const fields: AppToolFieldSchema[] = [];
    for (const field of objectValue.fields) {
      const fieldObject = asObject(field);
      if (!fieldObject) {
        continue;
      }
      const name = normalizeString(fieldObject.name);
      const label = normalizeString(fieldObject.label);
      const rawType = normalizeString(fieldObject.type) as AppToolFieldType | undefined;
      const type = rawType && SUPPORTED_FIELD_TYPES.has(rawType) ? rawType : 'text';
      if (!name || !label) {
        continue;
      }
      const options = Array.isArray(fieldObject.options)
        ? fieldObject.options
            .map((option) => {
              const optionObject = asObject(option);
              if (!optionObject) {
                return null;
              }
              const optionValue = normalizeString(optionObject.value);
              const optionLabel = normalizeString(optionObject.label) ?? optionValue;
              if (!optionValue || !optionLabel) {
                return null;
              }
              return { label: optionLabel, value: optionValue } satisfies AppToolFieldOption;
            })
            .filter((option): option is AppToolFieldOption => Boolean(option))
        : undefined;
      fields.push({
        name,
        label,
        type,
        required: normalizeBoolean(fieldObject.required),
        maxLength: normalizeNumber(fieldObject.maxLength),
        placeholder: normalizeString(fieldObject.placeholder),
        description: normalizeString(fieldObject.description) ?? normalizeString(fieldObject.helpText),
        rows: normalizeNumber(fieldObject.rows),
        min: normalizeNumber(fieldObject.min),
        max: normalizeNumber(fieldObject.max),
        step: normalizeNumber(fieldObject.step),
        options,
        defaultValue: normalizeFieldDefaultValue(type, fieldObject.defaultValue),
      });
    }
    return { fields };
  } catch {
    return { fields: [] };
  }
}

export function parseAppToolDefaultValues(raw: string | undefined): Record<string, AppToolFormValue> {
  try {
    const parsed = raw ? (JSON.parse(raw) as unknown) : {};
    const objectValue = asObject(parsed) ?? {};
    const values: Record<string, AppToolFormValue> = {};
    for (const [key, value] of Object.entries(objectValue)) {
      if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
        values[key] = value;
      }
    }
    return values;
  } catch {
    return {};
  }
}

export function buildDefaultValuesFromSchema(
  fields: AppToolFieldSchema[],
  rawDefaults: Record<string, AppToolFormValue>,
): Record<string, AppToolFormValue> {
  const values: Record<string, AppToolFormValue> = {};
  for (const field of fields) {
    if (Object.prototype.hasOwnProperty.call(rawDefaults, field.name)) {
      values[field.name] = rawDefaults[field.name];
      continue;
    }
    if (field.defaultValue !== null && field.defaultValue !== undefined) {
      values[field.name] = field.defaultValue;
      continue;
    }
    if (field.type === 'switch') {
      values[field.name] = false;
      continue;
    }
    if (field.type === 'number') {
      values[field.name] = undefined;
      continue;
    }
    values[field.name] = '';
  }
  return values;
}

export function parseAppToolResultSchema(raw: string | undefined): AppToolResultSchema {
  try {
    const parsed = raw ? (JSON.parse(raw) as unknown) : {};
    const objectValue = asObject(parsed);
    if (!objectValue) {
      return {};
    }
    const displayMode = normalizeString(objectValue.displayMode);
    return {
      displayMode: displayMode === 'json' ? 'json' : 'text',
      primaryField: normalizeString(objectValue.primaryField) ?? normalizeString(objectValue.resultField),
      copyable: normalizeBoolean(objectValue.copyable),
      rows: normalizeNumber(objectValue.rows),
    };
  } catch {
    return {};
  }
}

export function buildInputSchemaJson(fields: AppToolFieldSchema[]): string {
  return JSON.stringify({
    fields: fields.map((field) => ({
      name: field.name,
      label: field.label,
      type: field.type,
      required: Boolean(field.required),
      maxLength: field.maxLength,
      placeholder: field.placeholder,
      description: field.description,
      rows: field.rows,
      min: field.min,
      max: field.max,
      step: field.step,
      options: field.options?.length ? field.options : undefined,
      defaultValue: field.defaultValue,
    })),
  });
}

export function buildDefaultValuesJson(fields: AppToolFieldSchema[]): string {
  const values = fields.reduce<Record<string, unknown>>((accumulator, field) => {
    if (field.defaultValue !== null && field.defaultValue !== undefined) {
      accumulator[field.name] = field.defaultValue;
    }
    return accumulator;
  }, {});
  return JSON.stringify(values);
}

export function buildResultSchemaJson(schema: AppToolResultSchema): string {
  return JSON.stringify({
    displayMode: schema.displayMode ?? 'text',
    primaryField: schema.primaryField || undefined,
    copyable: Boolean(schema.copyable),
    rows: schema.rows,
  });
}

export function formatOptionsText(options: AppToolFieldOption[] | undefined): string {
  if (!options?.length) {
    return '';
  }
  return options.map((option) => `${option.label}:${option.value}`).join('\n');
}

export function parseOptionsText(rawText: string | undefined): AppToolFieldOption[] {
  if (!rawText?.trim()) {
    return [];
  }
  return Array.from(
    new Map(
      rawText
        .split(/\n+/)
        .map((line) => line.trim())
        .filter(Boolean)
        .map((line) => {
          const separatorIndex = line.indexOf(':');
          if (separatorIndex < 0) {
            return [line, { label: line, value: line }] as const;
          }
          const label = line.slice(0, separatorIndex).trim() || line.slice(separatorIndex + 1).trim();
          const value = line.slice(separatorIndex + 1).trim() || line.slice(0, separatorIndex).trim();
          return [value, { label, value }] as const;
        }),
    ).values(),
  );
}
