export default function SkeletonCard() {
  return (
    <div className="card animate-pulse" aria-hidden="true">
      <div className="h-4 bg-muted rounded w-3/4 mb-3" />
      <div className="h-3 bg-muted rounded w-1/2 mb-2" />
      <div className="h-3 bg-muted rounded w-1/3" />
    </div>
  )
}
