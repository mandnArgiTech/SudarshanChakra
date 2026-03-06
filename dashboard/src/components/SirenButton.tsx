import { clsx } from 'clsx';
import { Siren } from 'lucide-react';

interface SirenButtonProps {
  active?: boolean;
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary';
  loading?: boolean;
}

export default function SirenButton({
  active = false,
  label,
  onClick,
  variant = 'primary',
  loading = false,
}: SirenButtonProps) {
  const isPrimary = variant === 'primary';

  return (
    <button
      onClick={onClick}
      disabled={loading}
      className={clsx(
        'font-mono font-extrabold uppercase tracking-[2px] cursor-pointer transition-all duration-200 flex items-center justify-center gap-3',
        isPrimary
          ? clsx(
              'w-full py-4 rounded-xl text-lg border-2',
              active
                ? 'border-sc-success bg-sc-success/10 text-sc-success hover:bg-sc-success/20'
                : 'border-sc-critical bg-sc-critical/10 text-sc-critical hover:bg-sc-critical/20',
            )
          : 'py-2.5 px-6 rounded-lg text-[13px] border border-sc-high/30 bg-sc-high/5 text-sc-high hover:bg-sc-high/15',
        loading && 'opacity-50 cursor-not-allowed',
      )}
    >
      <Siren size={isPrimary ? 22 : 16} />
      {loading ? 'Processing...' : label}
    </button>
  );
}
