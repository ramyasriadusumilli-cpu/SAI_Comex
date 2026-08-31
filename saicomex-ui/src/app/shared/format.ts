import { Pipe, PipeTransform } from '@angular/core';

/**
 * Money, formatted for reading down a column.
 *
 * Always two decimals and always grouped: a column of figures where some rows
 * are `1200` and others `1,200.00` cannot be scanned, and this is a financial
 * system where scanning a column is the main thing people do.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency?: string | null): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '—';
    const formatted = value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return currency ? `${formatted} ${currency}` : formatted;
  }
}

/** Quantities: up to two decimals, trailing zeros trimmed. */
@Pipe({ name: 'qty' })
export class QuantityPipe implements PipeTransform {
  transform(value: number | null | undefined, unit?: string | null): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '—';
    const formatted = value.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    return unit ? `${formatted} ${unit}` : formatted;
  }
}

@Pipe({ name: 'pct' })
export class PercentPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '—';
    return `${value.toLocaleString('en-US', { maximumFractionDigits: 2 })}%`;
  }
}

/** ISO date → 05 Aug 2026. Unambiguous between UK and US readers. */
@Pipe({ name: 'shortDate' })
export class ShortDatePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}

@Pipe({ name: 'dateTime' })
export class DateTimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }
}

/** ACTIVE → Active, CONTRACT_PENDING → Contract pending. */
@Pipe({ name: 'statusLabel' })
export class StatusLabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '—';
    const words = value.toLowerCase().replace(/_/g, ' ');
    return words.charAt(0).toUpperCase() + words.slice(1);
  }
}

/** Maps a status onto one of the pill classes in styles.css. */
@Pipe({ name: 'statusClass' })
export class StatusClassPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    switch (value) {
      case 'ACTIVE': case 'APPROVED': case 'PAID': case 'COMPLETED': case 'CONFIRMED': case 'VERIFIED':
        return 'pill pill-active';
      case 'DRAFT': case 'PROPOSED': case 'PENDING_APPROVAL': case 'CONTRACT_PENDING':
      case 'MOBILISATION': case 'SUBMITTED': case 'PARTIALLY_PAID': case 'CALCULATED': case 'OPEN':
        return 'pill pill-pending';
      case 'SUSPENDED': case 'EXPIRED': case 'REJECTED': case 'TERMINATED': case 'CANCELLED':
      case 'CLOSED': case 'TEMPORARILY_STOPPED': case 'DISABLED':
        return 'pill pill-suspended';
      case 'DEVELOPMENT': case 'PROSPECTING': case 'PLANNING': case 'CONTRACTED':
        return 'pill pill-info';
      case 'CORRECTED': case 'SUPERSEDED':
        return 'pill pill-special';
      default:
        return 'pill';
    }
  }
}

/** Everything above, importable as one array. */
export const FORMAT_PIPES = [
  MoneyPipe, QuantityPipe, PercentPipe, ShortDatePipe, DateTimePipe, StatusLabelPipe, StatusClassPipe,
] as const;
