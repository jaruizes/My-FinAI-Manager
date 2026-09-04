import { decimal2, money, percent } from './valuation-format';

describe('valuation-format', () => {
  describe('money', () => {
    it('formats to 2 decimals with the currency symbol', () => {
      expect(money('2100', 'EUR')).toBe('€2,100.00');
      expect(money('2625', 'USD')).toBe('$2,625.00');
      expect(money('1600.5', 'EUR')).toBe('€1,600.50');
    });

    it('renders — for a missing value (never 0)', () => {
      expect(money(null, 'EUR')).toBe('—');
      expect(money(undefined, 'USD')).toBe('—');
      expect(money('', 'EUR')).toBe('—');
      expect(money('not-a-number', 'EUR')).toBe('—');
    });
  });

  describe('percent', () => {
    it('multiplies a fraction by 100 and shows 2 decimals', () => {
      expect(percent('0.761904761905')).toBe('76.19%');
      expect(percent('0.238095238095')).toBe('23.81%');
      expect(percent('1')).toBe('100.00%');
    });

    it('renders — for a missing value', () => {
      expect(percent(null)).toBe('—');
      expect(percent('')).toBe('—');
    });
  });

  describe('decimal2', () => {
    it('formats to 2 decimals or —', () => {
      expect(decimal2('200')).toBe('200.00');
      expect(decimal2(null)).toBe('—');
    });
  });
});
