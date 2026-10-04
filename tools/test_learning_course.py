import unittest
from build_learning_course import units, DATA

class LearningCourseTests(unittest.TestCase):
    def test_levels_and_original_unit_content(self):
        self.assertEqual(48, len(units))
        self.assertEqual(48, len({unit['id'] for unit in units}))
        for level in range(6):
            self.assertEqual(8, sum(unit['level'] == level for unit in units))
        for unit in units:
            for key in ('goal','question','example','meaning','transfer','criterion','explanation','pattern','check','checkCriterion'):
                self.assertTrue(unit[key].strip(), (unit['id'], key))
            self.assertTrue(any(0x1000 <= ord(c) <= 0x109f for c in unit['explanation']))
            self.assertTrue(any(0xac00 <= ord(c) <= 0xd7af for c in unit['question']))
    def test_advanced_has_work_and_discussion_and_new_situations(self):
        advanced = [unit for unit in units if unit['level'] == 5]
        self.assertIn('Factory', {u['topic'] for u in advanced})
        self.assertIn('Discussion', {u['topic'] for u in advanced})
        for unit in units:
            self.assertNotEqual(unit['question'], unit['transfer'])
            self.assertNotEqual(unit['example'], unit['transfer'])
            self.assertNotEqual(unit['check'], unit['transfer'])
            self.assertNotEqual(unit['check'], unit['question'])

if __name__ == '__main__':
    unittest.main()
