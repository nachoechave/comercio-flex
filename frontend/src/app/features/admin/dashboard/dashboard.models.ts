export interface LowStockVariant {
  variantId: string;
  productName: string;
  sku: string;
  size: string | null;
  color: string | null;
  quantity: string;
}

export interface DashboardSummary {
  currencyCode: string;
  timezone: string;
  lowStockThreshold: string;
  salesToday: string;
  salesThisMonth: string;
  openOrders: number;
  lowStockVariants: number;
  criticalStock: LowStockVariant[];
  generatedAt: string;
}
