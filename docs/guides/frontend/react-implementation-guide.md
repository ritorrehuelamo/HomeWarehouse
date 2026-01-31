# Frontend Implementation Guide - React + TypeScript

## Overview

This guide provides detailed patterns and best practices for implementing the HomeWarehouse frontend using React 18, TypeScript, and modern tooling.

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| React | 18.x | UI framework |
| TypeScript | 5.x | Type safety |
| Vite | 5.x | Build tool and dev server |
| React Router | 6.x | Client-side routing |
| TanStack Query | 5.x | Server state management |
| Zustand | 4.x | Client state management |
| React Hook Form | 7.x | Form handling |
| Zod | 3.x | Runtime validation |
| Axios | 1.x | HTTP client |
| TailwindCSS | 3.x | Styling |
| shadcn/ui | Latest | Component library |
| Vitest | 1.x | Unit testing |
| React Testing Library | 14.x | Component testing |

## Project Structure

```
web/
├── public/
│   └── assets/              # Static assets
├── src/
│   ├── main.tsx            # Application entry point
│   ├── App.tsx             # Root component
│   ├── router.tsx          # Route configuration
│   ├── api/                # API client layer
│   │   ├── client.ts       # Axios instance with interceptors
│   │   ├── auth.api.ts     # Auth endpoints
│   │   ├── ledger.api.ts   # Ledger endpoints
│   │   └── inventory.api.ts
│   ├── components/         # Reusable components
│   │   ├── ui/            # Base UI components (shadcn)
│   │   ├── forms/         # Form components
│   │   ├── layouts/       # Layout components
│   │   └── common/        # Common components
│   ├── features/          # Feature modules
│   │   ├── auth/
│   │   │   ├── components/
│   │   │   ├── hooks/
│   │   │   ├── stores/
│   │   │   ├── types/
│   │   │   └── utils/
│   │   ├── ledger/
│   │   └── inventory/
│   ├── hooks/             # Global custom hooks
│   ├── stores/            # Global Zustand stores
│   ├── types/             # Global TypeScript types
│   ├── utils/             # Utility functions
│   └── lib/               # Third-party lib configurations
├── tests/
│   ├── unit/              # Unit tests
│   └── integration/       # Integration tests
├── .env.example           # Environment variables template
├── tsconfig.json          # TypeScript configuration
├── vite.config.ts         # Vite configuration
└── package.json
```

## Core Patterns

### 1. API Client Layer

#### Base Axios Instance with Interceptors

```typescript
// src/api/client.ts
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/features/auth/stores/auth.store';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: `${API_BASE_URL}/api/v1`,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - add access token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken;

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor - handle token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Handle 401 errors (token expired)
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Attempt token refresh
        const authStore = useAuthStore.getState();
        const refreshToken = authStore.refreshToken;

        if (!refreshToken) {
          authStore.logout();
          return Promise.reject(error);
        }

        const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, {
          refreshToken,
        });

        const { accessToken, refreshToken: newRefreshToken, expiresAt } = response.data;

        // Update tokens in store
        authStore.setTokens({
          accessToken,
          refreshToken: newRefreshToken,
          expiresAt,
        });

        // Retry original request with new token
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        }

        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh failed, logout user
        useAuthStore.getState().logout();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
```

#### API Error Types

```typescript
// src/api/types.ts
export interface ApiError {
  code: string;
  message: string;
  correlationId: string;
  fields?: Record<string, string[]>;
}

export interface ApiResponse<T> {
  data: T;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
```

#### Feature API Module Example

```typescript
// src/api/ledger.api.ts
import apiClient from './client';
import { ApiResponse, PaginatedResponse } from './types';

export interface Transaction {
  id: string;
  accountId: string;
  type: 'INCOME' | 'EXPENSE';
  amount: {
    value: number;
    currency: string;
  };
  description: string;
  transactionDate: string;
  categoryId: string;
  createdAt: string;
}

export interface CreateTransactionRequest {
  accountId: string;
  type: 'INCOME' | 'EXPENSE';
  amount: number;
  currency: string;
  description: string;
  transactionDate: string;
  categoryId: string;
}

export interface TransactionFilters {
  accountId?: string;
  type?: 'INCOME' | 'EXPENSE';
  startDate?: string;
  endDate?: string;
  categoryId?: string;
  page?: number;
  size?: number;
}

export const ledgerApi = {
  // Create transaction
  createTransaction: async (
    data: CreateTransactionRequest
  ): Promise<Transaction> => {
    const response = await apiClient.post<ApiResponse<Transaction>>(
      '/ledger/transactions',
      data
    );
    return response.data.data;
  },

  // Get transaction by ID
  getTransaction: async (id: string): Promise<Transaction> => {
    const response = await apiClient.get<ApiResponse<Transaction>>(
      `/ledger/transactions/${id}`
    );
    return response.data.data;
  },

  // List transactions with filters
  listTransactions: async (
    filters: TransactionFilters
  ): Promise<PaginatedResponse<Transaction>> => {
    const response = await apiClient.get<PaginatedResponse<Transaction>>(
      '/ledger/transactions',
      { params: filters }
    );
    return response.data;
  },

  // Update transaction
  updateTransaction: async (
    id: string,
    data: Partial<CreateTransactionRequest>
  ): Promise<Transaction> => {
    const response = await apiClient.put<ApiResponse<Transaction>>(
      `/ledger/transactions/${id}`,
      data
    );
    return response.data.data;
  },

  // Delete transaction
  deleteTransaction: async (id: string): Promise<void> => {
    await apiClient.delete(`/ledger/transactions/${id}`);
  },
};
```

### 2. State Management

#### Auth Store (Zustand)

```typescript
// src/features/auth/stores/auth.store.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
}

interface AuthState {
  // State
  accessToken: string | null;
  refreshToken: string | null;
  expiresAt: string | null;
  isAuthenticated: boolean;

  // Actions
  setTokens: (tokens: AuthTokens) => void;
  logout: () => void;
  isTokenExpired: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      // Initial state
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      isAuthenticated: false,

      // Actions
      setTokens: (tokens) =>
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          expiresAt: tokens.expiresAt,
          isAuthenticated: true,
        }),

      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          expiresAt: null,
          isAuthenticated: false,
        }),

      isTokenExpired: () => {
        const { expiresAt } = get();
        if (!expiresAt) return true;

        const expirationTime = new Date(expiresAt).getTime();
        const currentTime = Date.now();

        // Consider expired if less than 1 minute remaining
        return expirationTime - currentTime < 60000;
      },
    }),
    {
      name: 'auth-storage',
      // Only persist refresh token, not access token (security)
      partialize: (state) => ({
        refreshToken: state.refreshToken,
        expiresAt: state.expiresAt,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
```

#### Feature Store Example

```typescript
// src/features/ledger/stores/transaction-filters.store.ts
import { create } from 'zustand';
import { TransactionFilters } from '@/api/ledger.api';

interface TransactionFiltersState {
  filters: TransactionFilters;
  setFilters: (filters: Partial<TransactionFilters>) => void;
  resetFilters: () => void;
}

const defaultFilters: TransactionFilters = {
  page: 0,
  size: 20,
};

export const useTransactionFiltersStore = create<TransactionFiltersState>()(
  (set) => ({
    filters: defaultFilters,

    setFilters: (newFilters) =>
      set((state) => ({
        filters: { ...state.filters, ...newFilters },
      })),

    resetFilters: () =>
      set({
        filters: defaultFilters,
      }),
  })
);
```

### 3. TanStack Query Integration

#### Query Client Setup

```typescript
// src/lib/react-query.ts
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      gcTime: 10 * 60 * 1000, // 10 minutes (formerly cacheTime)
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});

export function QueryProvider({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
}
```

#### Custom Hooks with TanStack Query

```typescript
// src/features/ledger/hooks/use-transactions.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ledgerApi, TransactionFilters } from '@/api/ledger.api';
import { useToast } from '@/components/ui/use-toast';

// Query key factory
export const transactionKeys = {
  all: ['transactions'] as const,
  lists: () => [...transactionKeys.all, 'list'] as const,
  list: (filters: TransactionFilters) =>
    [...transactionKeys.lists(), filters] as const,
  details: () => [...transactionKeys.all, 'detail'] as const,
  detail: (id: string) => [...transactionKeys.details(), id] as const,
};

// List transactions query
export function useTransactions(filters: TransactionFilters) {
  return useQuery({
    queryKey: transactionKeys.list(filters),
    queryFn: () => ledgerApi.listTransactions(filters),
    placeholderData: (previousData) => previousData,
  });
}

// Get single transaction query
export function useTransaction(id: string) {
  return useQuery({
    queryKey: transactionKeys.detail(id),
    queryFn: () => ledgerApi.getTransaction(id),
    enabled: !!id,
  });
}

// Create transaction mutation
export function useCreateTransaction() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: ledgerApi.createTransaction,
    onSuccess: () => {
      // Invalidate and refetch transactions list
      queryClient.invalidateQueries({ queryKey: transactionKeys.lists() });

      toast({
        title: 'Success',
        description: 'Transaction created successfully',
      });
    },
    onError: (error: any) => {
      toast({
        title: 'Error',
        description: error.response?.data?.message || 'Failed to create transaction',
        variant: 'destructive',
      });
    },
  });
}

// Update transaction mutation
export function useUpdateTransaction() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) =>
      ledgerApi.updateTransaction(id, data),
    onSuccess: (data) => {
      // Invalidate lists
      queryClient.invalidateQueries({ queryKey: transactionKeys.lists() });

      // Update detail cache
      queryClient.setQueryData(transactionKeys.detail(data.id), data);

      toast({
        title: 'Success',
        description: 'Transaction updated successfully',
      });
    },
    onError: (error: any) => {
      toast({
        title: 'Error',
        description: error.response?.data?.message || 'Failed to update transaction',
        variant: 'destructive',
      });
    },
  });
}

// Delete transaction mutation
export function useDeleteTransaction() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: ledgerApi.deleteTransaction,
    onSuccess: (_, deletedId) => {
      // Invalidate lists
      queryClient.invalidateQueries({ queryKey: transactionKeys.lists() });

      // Remove from cache
      queryClient.removeQueries({ queryKey: transactionKeys.detail(deletedId) });

      toast({
        title: 'Success',
        description: 'Transaction deleted successfully',
      });
    },
    onError: (error: any) => {
      toast({
        title: 'Error',
        description: error.response?.data?.message || 'Failed to delete transaction',
        variant: 'destructive',
      });
    },
  });
}
```

### 4. Form Handling with React Hook Form + Zod

#### Form Schema Definition

```typescript
// src/features/ledger/schemas/transaction.schema.ts
import { z } from 'zod';

export const transactionSchema = z.object({
  accountId: z.string().uuid('Invalid account'),

  type: z.enum(['INCOME', 'EXPENSE'], {
    required_error: 'Transaction type is required',
  }),

  amount: z.coerce
    .number({
      required_error: 'Amount is required',
      invalid_type_error: 'Amount must be a number',
    })
    .positive('Amount must be positive')
    .max(999999999.99, 'Amount is too large'),

  currency: z.string().length(3, 'Currency must be 3 characters'),

  description: z.string()
    .min(1, 'Description is required')
    .max(500, 'Description is too long'),

  transactionDate: z.string()
    .datetime('Invalid date format')
    .refine((date) => {
      const transactionDate = new Date(date);
      const today = new Date();
      return transactionDate <= today;
    }, 'Transaction date cannot be in the future'),

  categoryId: z.string().uuid('Invalid category'),
});

export type TransactionFormData = z.infer<typeof transactionSchema>;
```

#### Form Component

```typescript
// src/features/ledger/components/TransactionForm.tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { transactionSchema, TransactionFormData } from '../schemas/transaction.schema';
import { useCreateTransaction } from '../hooks/use-transactions';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface TransactionFormProps {
  onSuccess?: () => void;
}

export function TransactionForm({ onSuccess }: TransactionFormProps) {
  const createTransaction = useCreateTransaction();

  const form = useForm<TransactionFormData>({
    resolver: zodResolver(transactionSchema),
    defaultValues: {
      type: 'EXPENSE',
      currency: 'USD',
      transactionDate: new Date().toISOString().split('T')[0],
    },
  });

  const onSubmit = async (data: TransactionFormData) => {
    try {
      await createTransaction.mutateAsync(data);
      form.reset();
      onSuccess?.();
    } catch (error) {
      // Error is handled by the mutation hook
    }
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <FormField
          control={form.control}
          name="type"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Type</FormLabel>
              <Select onValueChange={field.onChange} defaultValue={field.value}>
                <FormControl>
                  <SelectTrigger>
                    <SelectValue placeholder="Select type" />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  <SelectItem value="INCOME">Income</SelectItem>
                  <SelectItem value="EXPENSE">Expense</SelectItem>
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="amount"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Amount</FormLabel>
              <FormControl>
                <Input
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="description"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Description</FormLabel>
              <FormControl>
                <Input placeholder="Enter description" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="transactionDate"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Date</FormLabel>
              <FormControl>
                <Input type="date" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <Button
          type="submit"
          disabled={createTransaction.isPending}
          className="w-full"
        >
          {createTransaction.isPending ? 'Creating...' : 'Create Transaction'}
        </Button>
      </form>
    </Form>
  );
}
```

### 5. Routing and Protected Routes

#### Route Configuration

```typescript
// src/router.tsx
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { MainLayout } from './components/layouts/MainLayout';
import { AuthLayout } from './components/layouts/AuthLayout';

// Lazy-loaded pages
import { lazy } from 'react';

const LoginPage = lazy(() => import('./features/auth/pages/LoginPage'));
const DashboardPage = lazy(() => import('./features/dashboard/pages/DashboardPage'));
const TransactionsPage = lazy(() => import('./features/ledger/pages/TransactionsPage'));
const InventoryPage = lazy(() => import('./features/inventory/pages/InventoryPage'));

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/dashboard" replace />,
  },
  {
    path: '/auth',
    element: <AuthLayout />,
    children: [
      {
        path: 'login',
        element: <LoginPage />,
      },
    ],
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <MainLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        path: 'dashboard',
        element: <DashboardPage />,
      },
      {
        path: 'transactions',
        element: <TransactionsPage />,
      },
      {
        path: 'inventory',
        element: <InventoryPage />,
      },
    ],
  },
]);
```

#### Protected Route Component

```typescript
// src/components/auth/ProtectedRoute.tsx
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/stores/auth.store';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isTokenExpired } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated || isTokenExpired()) {
    // Redirect to login, preserving the intended destination
    return <Navigate to="/auth/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
```

### 6. Error Handling

#### Global Error Boundary

```typescript
// src/components/ErrorBoundary.tsx
import React, { Component, ErrorInfo, ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { AlertCircle } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uncaught error:', error, errorInfo);

    // Send to error tracking service (e.g., Sentry)
    // errorTrackingService.captureException(error, errorInfo);
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: undefined });
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50">
          <div className="max-w-md rounded-lg bg-white p-8 shadow-lg">
            <div className="mb-4 flex items-center gap-2 text-red-600">
              <AlertCircle className="h-6 w-6" />
              <h1 className="text-xl font-bold">Something went wrong</h1>
            </div>

            <p className="mb-4 text-gray-600">
              An unexpected error occurred. Please try refreshing the page.
            </p>

            {import.meta.env.DEV && this.state.error && (
              <pre className="mb-4 overflow-auto rounded bg-gray-100 p-4 text-xs">
                {this.state.error.message}
              </pre>
            )}

            <div className="flex gap-2">
              <Button onClick={this.handleReset}>Try Again</Button>
              <Button
                variant="outline"
                onClick={() => window.location.href = '/'}
              >
                Go Home
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
```

#### API Error Handler Hook

```typescript
// src/hooks/use-api-error.ts
import { AxiosError } from 'axios';
import { ApiError } from '@/api/types';

export function useApiError() {
  const getErrorMessage = (error: unknown): string => {
    if (error instanceof AxiosError) {
      const apiError = error.response?.data as ApiError;

      if (apiError?.message) {
        return apiError.message;
      }

      // Handle field validation errors
      if (apiError?.fields) {
        const fieldErrors = Object.entries(apiError.fields)
          .map(([field, messages]) => `${field}: ${messages.join(', ')}`)
          .join('; ');
        return fieldErrors;
      }

      // HTTP error codes
      switch (error.response?.status) {
        case 400:
          return 'Invalid request. Please check your input.';
        case 401:
          return 'You are not authorized. Please log in.';
        case 403:
          return 'You do not have permission to perform this action.';
        case 404:
          return 'The requested resource was not found.';
        case 409:
          return 'This action conflicts with existing data.';
        case 429:
          return 'Too many requests. Please try again later.';
        case 500:
          return 'Server error. Please try again later.';
        default:
          return 'An unexpected error occurred.';
      }
    }

    if (error instanceof Error) {
      return error.message;
    }

    return 'An unknown error occurred.';
  };

  const getCorrelationId = (error: unknown): string | undefined => {
    if (error instanceof AxiosError) {
      const apiError = error.response?.data as ApiError;
      return apiError?.correlationId;
    }
    return undefined;
  };

  return { getErrorMessage, getCorrelationId };
}
```

### 7. Testing

#### Component Test Example

```typescript
// src/features/ledger/components/__tests__/TransactionForm.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TransactionForm } from '../TransactionForm';
import { vi } from 'vitest';

// Mock the API
vi.mock('@/api/ledger.api', () => ({
  ledgerApi: {
    createTransaction: vi.fn(),
  },
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
};

describe('TransactionForm', () => {
  it('renders all form fields', () => {
    render(<TransactionForm />, { wrapper: createWrapper() });

    expect(screen.getByLabelText(/type/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/amount/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/date/i)).toBeInTheDocument();
  });

  it('validates required fields', async () => {
    const user = userEvent.setup();
    render(<TransactionForm />, { wrapper: createWrapper() });

    const submitButton = screen.getByRole('button', { name: /create/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/description is required/i)).toBeInTheDocument();
    });
  });

  it('validates amount is positive', async () => {
    const user = userEvent.setup();
    render(<TransactionForm />, { wrapper: createWrapper() });

    const amountInput = screen.getByLabelText(/amount/i);
    await user.type(amountInput, '-100');

    const submitButton = screen.getByRole('button', { name: /create/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/amount must be positive/i)).toBeInTheDocument();
    });
  });

  it('submits form with valid data', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    const { ledgerApi } = await import('@/api/ledger.api');

    vi.mocked(ledgerApi.createTransaction).mockResolvedValue({
      id: '123',
      accountId: 'acc-1',
      type: 'EXPENSE',
      amount: { value: 100, currency: 'USD' },
      description: 'Test transaction',
      transactionDate: '2024-01-15',
      categoryId: 'cat-1',
      createdAt: '2024-01-15T10:00:00Z',
    });

    render(<TransactionForm onSuccess={onSuccess} />, { wrapper: createWrapper() });

    await user.type(screen.getByLabelText(/amount/i), '100');
    await user.type(screen.getByLabelText(/description/i), 'Test transaction');

    const submitButton = screen.getByRole('button', { name: /create/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(ledgerApi.createTransaction).toHaveBeenCalled();
      expect(onSuccess).toHaveBeenCalled();
    });
  });
});
```

#### Hook Test Example

```typescript
// src/features/ledger/hooks/__tests__/use-transactions.test.ts
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useTransactions, useCreateTransaction } from '../use-transactions';
import { vi } from 'vitest';

vi.mock('@/api/ledger.api');

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
};

describe('useTransactions', () => {
  it('fetches transactions successfully', async () => {
    const { ledgerApi } = await import('@/api/ledger.api');
    const mockData = {
      content: [
        { id: '1', description: 'Transaction 1' },
        { id: '2', description: 'Transaction 2' },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      last: true,
    };

    vi.mocked(ledgerApi.listTransactions).mockResolvedValue(mockData);

    const { result } = renderHook(
      () => useTransactions({ page: 0, size: 20 }),
      { wrapper: createWrapper() }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
    expect(ledgerApi.listTransactions).toHaveBeenCalledWith({ page: 0, size: 20 });
  });
});

describe('useCreateTransaction', () => {
  it('creates transaction and invalidates cache', async () => {
    const { ledgerApi } = await import('@/api/ledger.api');
    const mockTransaction = {
      id: '123',
      description: 'New transaction',
    };

    vi.mocked(ledgerApi.createTransaction).mockResolvedValue(mockTransaction as any);

    const { result } = renderHook(() => useCreateTransaction(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      accountId: 'acc-1',
      type: 'EXPENSE',
      amount: 100,
      currency: 'USD',
      description: 'New transaction',
      transactionDate: '2024-01-15',
      categoryId: 'cat-1',
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(ledgerApi.createTransaction).toHaveBeenCalled();
  });
});
```

## Best Practices

### 1. Performance Optimization

#### Code Splitting

```typescript
// Lazy load routes
const DashboardPage = lazy(() => import('./features/dashboard/pages/DashboardPage'));

// Lazy load heavy components
const HeavyChart = lazy(() => import('./components/charts/HeavyChart'));

function MyComponent() {
  return (
    <Suspense fallback={<Spinner />}>
      <HeavyChart data={data} />
    </Suspense>
  );
}
```

#### Memoization

```typescript
import { useMemo, useCallback } from 'react';

function ExpensiveComponent({ data }: { data: Item[] }) {
  // Memoize expensive calculations
  const sortedData = useMemo(() => {
    return data.sort((a, b) => a.value - b.value);
  }, [data]);

  // Memoize callbacks
  const handleClick = useCallback((id: string) => {
    // handle click
  }, []);

  return <div>{/* render */}</div>;
}
```

### 2. Accessibility

```typescript
// Proper semantic HTML
<button type="button" aria-label="Close dialog">
  <X className="h-4 w-4" />
</button>

// Keyboard navigation
function Dialog() {
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [onClose]);

  return <div role="dialog" aria-modal="true">{/* content */}</div>;
}
```

### 3. Type Safety

```typescript
// Use discriminated unions for different states
type RequestState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: Error };

// Exhaustive type checking
function handleState(state: RequestState<Transaction>) {
  switch (state.status) {
    case 'idle':
      return <div>Not started</div>;
    case 'loading':
      return <Spinner />;
    case 'success':
      return <div>{state.data.description}</div>;
    case 'error':
      return <div>Error: {state.error.message}</div>;
    default:
      // TypeScript ensures all cases are handled
      const _exhaustive: never = state;
      return _exhaustive;
  }
}
```

### 4. Environment Configuration

```typescript
// .env.example
VITE_API_BASE_URL=http://localhost:8080
VITE_ENVIRONMENT=development

// src/config/env.ts
import { z } from 'zod';

const envSchema = z.object({
  API_BASE_URL: z.string().url(),
  ENVIRONMENT: z.enum(['development', 'staging', 'production']),
});

function validateEnv() {
  const env = {
    API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
    ENVIRONMENT: import.meta.env.VITE_ENVIRONMENT,
  };

  const parsed = envSchema.safeParse(env);

  if (!parsed.success) {
    console.error('Invalid environment variables:', parsed.error.format());
    throw new Error('Invalid environment variables');
  }

  return parsed.data;
}

export const env = validateEnv();
```

## Summary

This guide covers:
- API client setup with token refresh interceptors
- State management with Zustand
- Server state with TanStack Query
- Form handling with React Hook Form and Zod
- Routing with protected routes
- Comprehensive error handling
- Testing strategies
- Performance optimization
- Type safety patterns
- Accessibility considerations

Follow these patterns consistently across all features for a maintainable, scalable frontend application.
