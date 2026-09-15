import { Home, User, Briefcase, FileText } from 'lucide-react'
import { NavBar } from "@/components/ui/tubelight-navbar"

export function NavBarDemo() {
  const navItems = [
    { name: 'Home', url: '#home', icon: Home },
    { name: 'About', url: '#about', icon: User },
    { name: 'Rankings', url: '#rankings', icon: Briefcase },
    { name: 'Downloads', url: '#downloads', icon: FileText }
  ]

  return <NavBar items={navItems} />
}
