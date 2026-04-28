import { useState } from 'react'
import './App.css'
import IndexPanel from './components/IndexPanel'
import SearchPanel from './components/SearchPanel'

function App() {
  const [activeSection, setActiveSection] = useState<'index' | 'search'>('search')
  return (
    <main className="container">
      <header className="app-header">
        <div className="app-header-top">
          <h1>Local File Search</h1>
          <nav className="app-nav" aria-label="Primary">
            <button
              type="button"
              className={activeSection === 'index' ? 'nav-tab active' : 'nav-tab'}
              onClick={() => setActiveSection('index')}
            >
              Index
            </button>
            <button
              type="button"
              className={activeSection === 'search' ? 'nav-tab active' : 'nav-tab'}
              onClick={() => setActiveSection('search')}
            >
              Search
            </button>
          </nav>
        </div>
      </header>

      {activeSection === 'index' ? <IndexPanel /> : <SearchPanel />}
    </main>
  )
}

export default App
