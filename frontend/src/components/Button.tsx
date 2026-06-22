import { clsx } from "clsx";
import type { ButtonHTMLAttributes } from "react";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
}

const variants: Record<ButtonVariant, string> = {
  primary: "bg-brand text-white hover:bg-blue-700 disabled:bg-blue-300",
  secondary: "border border-line bg-white text-ink hover:bg-slate-50 disabled:text-slate-400",
  ghost: "text-slate-700 hover:bg-slate-100 disabled:text-slate-400",
  danger: "bg-danger text-white hover:bg-red-700 disabled:bg-red-300",
};

export function Button({ className, variant = "secondary", ...props }: ButtonProps) {
  return (
    <button
      {...props}
      className={clsx(
        "inline-flex h-10 items-center justify-center gap-2 rounded px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-brand/25 disabled:cursor-not-allowed",
        variants[variant],
        className,
      )}
    />
  );
}
