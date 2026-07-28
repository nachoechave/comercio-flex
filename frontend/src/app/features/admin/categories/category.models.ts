export interface Category {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SaveCategory {
  name: string;
}

export interface CategoryStatusChange {
  active: boolean;
}

export interface FieldProblem {
  field: string;
  message: string;
}

export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  fieldErrors?: FieldProblem[] | Record<string, string | string[]>;
  errors?: Record<string, string | string[]>;
}
