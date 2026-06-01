export function decimalAmountToMinorUnits(amount: number): string {
  return BigInt(Math.round(amount * 100)).toString();
}
